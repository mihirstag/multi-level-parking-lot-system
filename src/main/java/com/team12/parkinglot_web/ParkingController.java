package com.team12.parkinglot_web; 

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import javax.servlet.http.HttpSession;

import com.team12.parkinglot_web.service.ParkingApplicationService;

@Controller
public class ParkingController {

    private final ParkingApplicationService parkingApplicationService;

    public ParkingController(ParkingApplicationService parkingApplicationService) {
        this.parkingApplicationService = parkingApplicationService;
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
        boolean success = parkingApplicationService.registerDriver(firstName, lastName, email, password);
        if(success) return "redirect:/login?registered=true";
        return "redirect:/register?error=true";
    }

    // --- NEW: Process Login & Create Session ---
    @PostMapping("/process-login")
    public String processLogin(@RequestParam("email") String email, 
                               @RequestParam("password") String password, 
                               HttpSession session) {
        if (parkingApplicationService.loginDriver(email, password)) {
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
        
        model.addAttribute("floors", parkingApplicationService.getFloors());
        return "spots";
    }

    // --- FIXED: Book Spot (No double booking!) ---
    @PostMapping("/book")
    public String bookSpot(@RequestParam("spotId") String spotId, HttpSession session) {
        if (session.getAttribute("userEmail") == null) return "redirect:/login";

        String userEmail = (String) session.getAttribute("userEmail");
        parkingApplicationService.bookSpot(spotId, userEmail);
        return "redirect:/spots"; // Stay on map!
    }

    // 1. New Multi-Spot Checkout (POST instead of GET)
    @PostMapping("/checkout")
    public String processCart(@RequestParam("selectedSpots") String selectedSpots, 
                              @RequestParam("hours") int hours, 
                              Model model) {
        ParkingApplicationService.CheckoutSummary checkoutSummary = parkingApplicationService.buildCheckoutSummary(selectedSpots, hours);

        model.addAttribute("selectedSpots", checkoutSummary.getSelectedSpots());
        model.addAttribute("hours", checkoutSummary.getHours());
        model.addAttribute("fee", checkoutSummary.getFee());
        model.addAttribute("spotCount", checkoutSummary.getSpotCount());
        
        return "checkout";
    }

    // 2. Show payment form (Credit Card / PayPal / Net Banking)
    @PostMapping("/payment")
    public String showPaymentForm(@RequestParam("selectedSpots") String selectedSpots,
                                  @RequestParam("hours") int hours,
                                  Model model) {
        ParkingApplicationService.CheckoutSummary checkoutSummary = parkingApplicationService.buildCheckoutSummary(selectedSpots, hours);

        model.addAttribute("selectedSpots", checkoutSummary.getSelectedSpots());
        model.addAttribute("hours", checkoutSummary.getHours());
        model.addAttribute("fee", checkoutSummary.getFee());
        model.addAttribute("spotCount", checkoutSummary.getSpotCount());
        
        return "payment";
    }

    // 3. Process Upfront Payment for All Spots
    @PostMapping("/pay")
    public String finalizePayment(@RequestParam("selectedSpots") String selectedSpots, 
                                  @RequestParam("hours") int hours, 
                                  @RequestParam("paymentMethod") String paymentMethod,
                                  @RequestParam(value = "bankCode", required = false) String bankCode,
                                  HttpSession session) {
        if (session.getAttribute("userEmail") == null) return "redirect:/login";

        String userEmail = (String) session.getAttribute("userEmail");
        parkingApplicationService.finalizePayment(selectedSpots, hours, paymentMethod, bankCode, userEmail);
        return "redirect:/success";
    }

    @GetMapping("/success")
    public String showSuccess(HttpSession session) {
        if (session.getAttribute("userEmail") == null) return "redirect:/login";
        return "success";
    }

    @GetMapping("/bookings")
    public String viewBookings(HttpSession session, Model model) {
        String userEmail = (String) session.getAttribute("userEmail");
        if (userEmail == null) return "redirect:/login";
        
        java.util.List<java.util.Map<String, String>> tickets = parkingApplicationService.getTicketsForUser(userEmail);
        model.addAttribute("tickets", tickets);
        return "bookings";
    }
}