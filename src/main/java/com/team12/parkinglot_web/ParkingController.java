package com.team12.parkinglot_web; 

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import jakarta.servlet.http.HttpSession;

import parkinglot.core.ParkingFloor;
import parkinglot.core.ParkingLot;
import parkinglot.enums.SpotType;
import parkinglot.spots.SpotFactory;
import parkinglot.spots.ParkingSpot;
import parkinglot.db.DatabaseHelper;
import java.util.UUID;

@Controller
public class ParkingController {

    public ParkingController() {
        DatabaseHelper.initializeDatabase();
        ParkingLot lot = ParkingLot.getInstance("123 Tech Park, Bengaluru");
        if (lot.getFloors().isEmpty()) {
            for (int i = 1; i <= 3; i++) {
                ParkingFloor floor = new ParkingFloor(i);
                for (int j = 1; j <= 20; j++) floor.addSpot(SpotFactory.createSpot("F" + i + "-C" + j, SpotType.COMPACT));
                for (int j = 1; j <= 20; j++) floor.addSpot(SpotFactory.createSpot("F" + i + "-L" + j, SpotType.LARGE));
                for (int j = 1; j <= 5; j++) floor.addSpot(SpotFactory.createSpot("F" + i + "-H" + j, SpotType.HANDICAPPED));
                for (int j = 1; j <= 5; j++) floor.addSpot(SpotFactory.createSpot("F" + i + "-E" + j, SpotType.EV_CHARGING));
                lot.addFloor(floor);
            }
        }
    }

    @GetMapping("/")
    public String showHome() { return "index"; }

    @GetMapping("/login")
    public String showLogin() { return "login"; }

    @GetMapping("/register")
    public String showRegister() { return "register"; }

    // --- NEW: Process Registration ---
    @PostMapping("/process-register")
    public String processRegister(@RequestParam("firstName") String firstName, 
                                  @RequestParam("lastName") String lastName, 
                                  @RequestParam("email") String email, 
                                  @RequestParam("password") String password) {
        boolean success = DatabaseHelper.registerDriver(firstName + " " + lastName, email, password);
        if(success) return "redirect:/login?registered=true";
        return "redirect:/register?error=true";
    }

    // --- NEW: Process Login & Create Session ---
    @PostMapping("/process-login")
    public String processLogin(@RequestParam("email") String email, 
                               @RequestParam("password") String password, 
                               HttpSession session) {
        if (DatabaseHelper.loginDriver(email, password)) {
            session.setAttribute("userEmail", email); // User is logged in!
            return "redirect:/dashboard/driver";
        }
        return "redirect:/login?error=true";
    }

    @GetMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate(); // Destroy session
        return "redirect:/login";
    }

    // --- DASHBOARD ---
    @GetMapping("/dashboard/driver")
    public String viewDriverDashboard(HttpSession session, Model model) {
        if (session.getAttribute("userEmail") == null) return "redirect:/login"; // Security block!
        
        model.addAttribute("systemName", "Driver Portal");
        model.addAttribute("welcomeMessage", "Welcome, " + session.getAttribute("userEmail") + "!");
        return "dashboard"; 
    }

    @GetMapping("/spots")
    public String viewSpots(HttpSession session, Model model) {
        if (session.getAttribute("userEmail") == null) return "redirect:/login"; // Security block!
        
        ParkingLot lot = ParkingLot.getInstance("123 Tech Park, Bengaluru");
        model.addAttribute("floors", lot.getFloors());
        return "spots";
    }

    // --- FIXED: Book Spot (No double booking!) ---
    @PostMapping("/book")
    public String bookSpot(@RequestParam("spotId") String spotId, HttpSession session) {
        if (session.getAttribute("userEmail") == null) return "redirect:/login";
        
        ParkingLot lot = ParkingLot.getInstance("123 Tech Park, Bengaluru");
        for (ParkingFloor floor : lot.getFloors()) {
            for (ParkingSpot spot : floor.getSpots()) {
                if (spot.getId().equals(spotId) && spot.isFree()) {
                    spot.book(1); // Default to 1 hour reservation for now
                    floor.updateDisplay(); 
                    String ticketId = "TKT-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
                    String userEmail = (String) session.getAttribute("userEmail");
                    DatabaseHelper.saveTicket(ticketId, userEmail, spotId, "RESERVED");
                }
            }
        }
        return "redirect:/spots"; // Stay on map!
    }

    // 1. New Multi-Spot Checkout (POST instead of GET)
    @PostMapping("/checkout")
    public String processCart(@RequestParam("selectedSpots") String selectedSpots, 
                              @RequestParam("hours") int hours, 
                              Model model) {
        // Split the comma-separated string into an array (e.g., ["F1-C1", "F1-C2"])
        String[] spotsArray = selectedSpots.split(",");
        
        // Calculate the fee: (Number of spots) * (Hours) * (₹50 per hour)
        double totalFee = spotsArray.length * hours * 50.0; 
        
        model.addAttribute("selectedSpots", selectedSpots);
        model.addAttribute("hours", hours);
        model.addAttribute("fee", totalFee);
        model.addAttribute("spotCount", spotsArray.length);
        
        return "checkout";
    }

    // 2. Process Upfront Payment for All Spots
    @PostMapping("/pay")
    public String finalizePayment(@RequestParam("selectedSpots") String selectedSpots, 
                                  @RequestParam("hours") int hours, 
                                  HttpSession session) {
        if (session.getAttribute("userEmail") == null) return "redirect:/login";
        
        ParkingLot lot = ParkingLot.getInstance("123 Tech Park, Bengaluru");
        String[] spotsArray = selectedSpots.split(",");
        
        // Loop through every spot the user selected
        for (String spotId : spotsArray) {
            spotId = spotId.trim();
            for (ParkingFloor floor : lot.getFloors()) {
                for (ParkingSpot spot : floor.getSpots()) {
                    if (spot.getId().equals(spotId) && spot.isFree()) {
                        
                        spot.book(hours); // Marks the spot as yellow/unavailable on the map and sets expiry
                        floor.updateDisplay();
                        
                        // Generate a ticket and save it instantly as PAID
                        String ticketId = "TKT-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
                        String userEmail = (String) session.getAttribute("userEmail");
                        DatabaseHelper.saveTicket(ticketId, userEmail, spotId, "PAID");
                    }
                }
            }
        }
        return "redirect:/dashboard/driver?payment=success";
    }

    @GetMapping("/bookings")
    public String viewBookings(HttpSession session, Model model) {
        String userEmail = (String) session.getAttribute("userEmail");
        if (userEmail == null) return "redirect:/login";
        
        java.util.List<java.util.Map<String, String>> tickets = DatabaseHelper.getTicketsForUser(userEmail);
        model.addAttribute("tickets", tickets);
        return "bookings";
    }
}