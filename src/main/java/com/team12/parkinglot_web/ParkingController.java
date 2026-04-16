package com.team12.parkinglot_web; 

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import javax.servlet.http.HttpSession;

import com.team12.parkinglot_web.payment.PaymentRequest;
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
        try {
            boolean success = parkingApplicationService.registerDriver(firstName, lastName, email, password);
            if (success) {
                return "redirect:/login?registered=true";
            }
            return "redirect:/register?error=exists";
        } catch (IllegalArgumentException ex) {
            return "redirect:/register?error=validation";
        }
    }

    // --- NEW: Process Login & Create Session ---
    @PostMapping("/process-login")
    public String processLogin(@RequestParam("email") String email, 
                               @RequestParam("password") String password, 
                               HttpSession session) {
        try {
            if (parkingApplicationService.loginDriver(email, password)) {
                session.setAttribute("userEmail", email.trim().toLowerCase());
                return "redirect:/dashboard/driver";
            }
            return "redirect:/login?error=true";
        } catch (IllegalArgumentException ex) {
            return "redirect:/login?error=validation";
        }
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
        try {
            boolean booked = parkingApplicationService.bookSpot(spotId, userEmail);
            if (!booked) {
                return "redirect:/spots?bookError=unavailable";
            }
            return "redirect:/spots";
        } catch (IllegalArgumentException ex) {
            return "redirect:/spots?bookError=validation";
        }
    }

    // 1. New Multi-Spot Checkout (POST instead of GET)
    @PostMapping("/checkout")
    public String processCart(@RequestParam("selectedSpots") String selectedSpots, 
                              @RequestParam("hours") int hours, 
                              Model model) {
        try {
            ParkingApplicationService.CheckoutSummary checkoutSummary = parkingApplicationService.buildCheckoutSummary(selectedSpots, hours);

            model.addAttribute("selectedSpots", checkoutSummary.getSelectedSpots());
            model.addAttribute("hours", checkoutSummary.getHours());
            model.addAttribute("fee", checkoutSummary.getFee());
            model.addAttribute("spotCount", checkoutSummary.getSpotCount());

            return "checkout";
        } catch (IllegalArgumentException ex) {
            return "redirect:/spots?cartError=validation";
        }
    }

    // 2. Show payment form (Credit Card / PayPal / Net Banking)
    @PostMapping("/payment")
    public String showPaymentForm(@RequestParam("selectedSpots") String selectedSpots,
                                  @RequestParam("hours") int hours,
                                  Model model) {
        try {
            ParkingApplicationService.CheckoutSummary checkoutSummary = parkingApplicationService.buildCheckoutSummary(selectedSpots, hours);

            model.addAttribute("selectedSpots", checkoutSummary.getSelectedSpots());
            model.addAttribute("hours", checkoutSummary.getHours());
            model.addAttribute("fee", checkoutSummary.getFee());
            model.addAttribute("spotCount", checkoutSummary.getSpotCount());

            return "payment";
        } catch (IllegalArgumentException ex) {
            return "redirect:/spots?cartError=validation";
        }
    }

    // 3. Process Upfront Payment for All Spots
    @PostMapping("/pay")
    public String finalizePayment(@RequestParam("selectedSpots") String selectedSpots, 
                                  @RequestParam("hours") int hours, 
                                  @RequestParam("paymentMethod") String paymentMethod,
                                  @RequestParam(value = "cardOwner", required = false) String cardOwner,
                                  @RequestParam(value = "cardNumber", required = false) String cardNumber,
                                  @RequestParam(value = "expiryMonth", required = false) String expiryMonth,
                                  @RequestParam(value = "expiryYear", required = false) String expiryYear,
                                  @RequestParam(value = "cvv", required = false) String cvv,
                                  @RequestParam(value = "bank", required = false) String bank,
                                  @RequestParam(value = "bankCode", required = false) String bankCode,
                                  HttpSession session) {
        if (session.getAttribute("userEmail") == null) return "redirect:/login";

        String userEmail = (String) session.getAttribute("userEmail");
        PaymentRequest paymentRequest = new PaymentRequest(
                paymentMethod,
                cardOwner,
                cardNumber,
                expiryMonth,
                expiryYear,
                cvv,
                bank,
                bankCode
        );

        try {
            boolean success = parkingApplicationService.finalizePayment(selectedSpots, hours, paymentRequest, userEmail);
            if (success) {
                return "redirect:/success";
            }
            return "redirect:/spots?paymentError=unavailable";
        } catch (IllegalArgumentException ex) {
            return "redirect:/spots?paymentError=validation";
        }
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