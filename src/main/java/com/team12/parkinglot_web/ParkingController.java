package com.team12.parkinglot_web; 

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import javax.servlet.http.HttpSession;

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

    @PostMapping("/process-register")
    public String processRegister(@RequestParam("firstName") String firstName, 
                                  @RequestParam("lastName") String lastName, 
                                  @RequestParam("email") String email, 
                                  @RequestParam("password") String password) {
        boolean success = DatabaseHelper.registerDriver(firstName + " " + lastName, email, password);
        if(success) return "redirect:/login?registered=true";
        return "redirect:/register?error=true";
    }

    @PostMapping("/process-login")
    public String processLogin(@RequestParam("email") String email, 
                               @RequestParam("password") String password, 
                               HttpSession session) {
        if (DatabaseHelper.loginDriver(email, password)) {
            session.setAttribute("userEmail", email);
            return "redirect:/dashboard/driver";
        }
        return "redirect:/login?error=true";
    }

    @GetMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/login";
    }

    @GetMapping("/dashboard/driver")
    public String viewDriverDashboard(HttpSession session, Model model) {
        if (session.getAttribute("userEmail") == null) return "redirect:/login";
        model.addAttribute("systemName", "Driver Portal");
        model.addAttribute("welcomeMessage", "Welcome, " + session.getAttribute("userEmail") + "!");
        return "dashboard"; 
    }

    @GetMapping("/spots")
    public String viewSpots(HttpSession session, Model model) {
        if (session.getAttribute("userEmail") == null) return "redirect:/login";
        ParkingLot lot = ParkingLot.getInstance("123 Tech Park, Bengaluru");
        
        // Reset all spots to available first, then mark reserved spots (so paid spots become green again)
        java.util.List<String> reservedSpots = DatabaseHelper.getReservedSpots();
        for (ParkingFloor floor : lot.getFloors()) {
            for (ParkingSpot spot : floor.getSpots()) {
                spot.release(); // Always reset to available on each render
                if (reservedSpots.contains(spot.getId())) {
                    spot.book(); // Reserved but not paid
                }
            }
        }
        
        model.addAttribute("floors", lot.getFloors());
        return "spots";
    }

    @PostMapping("/checkout")
    public String processCart(@RequestParam("selectedSpots") String selectedSpots, 
                              @RequestParam("hours") int hours, 
                              Model model) {
        String[] spotsArray = selectedSpots.split(",");
        double totalFee = spotsArray.length * hours * 50.0; 
        
        // Reserve all spots when user goes to checkout
        for (String spotId : spotsArray) {
            spotId = spotId.trim();
            String ticketId = "TKT-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
            DatabaseHelper.saveReservation(ticketId, spotId);
            model.addAttribute("ticketId_" + spotId, ticketId); // Store for payment
        }
        
        model.addAttribute("selectedSpots", selectedSpots);
        model.addAttribute("hours", hours);
        model.addAttribute("fee", totalFee);
        model.addAttribute("spotCount", spotsArray.length);
        
        return "checkout";
    }

    @PostMapping("/pay")
    public String finalizePayment(@RequestParam("selectedSpots") String selectedSpots, 
                                  @RequestParam("fee") double fee,
                                  HttpSession session) {
        if (session.getAttribute("userEmail") == null) return "redirect:/login";
        
        ParkingLot lot = ParkingLot.getInstance("123 Tech Park, Bengaluru");
        String[] spotsArray = selectedSpots.split(",");
        
        for (String spotId : spotsArray) {
            spotId = spotId.trim();
            for (ParkingFloor floor : lot.getFloors()) {
                for (ParkingSpot spot : floor.getSpots()) {
                    if (spot.getId().equals(spotId) && spot.isFree()) {
                        spot.book(); 
                        floor.updateDisplay();
                        
                        String ticketId = "TKT-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
                        // Save the payment record and the ticket
                        DatabaseHelper.saveTicket(ticketId, spotId, "PAID");
                        DatabaseHelper.savePayment(ticketId, fee / spotsArray.length); 
                    }
                }
            }
        }
        // Redirect to the dedicated success page
        return "redirect:/payment-success";
    }

    @GetMapping("/payment-success")
    public String showSuccessPage(HttpSession session) {
        if (session.getAttribute("userEmail") == null) return "redirect:/login";
        return "payment"; 
    }

    @GetMapping("/payment")
    public String showPaymentForm(HttpSession session, Model model) {
        if (session.getAttribute("userEmail") == null) return "redirect:/login";
        return "payment";
    }
}