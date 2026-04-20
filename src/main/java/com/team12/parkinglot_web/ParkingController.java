package com.team12.parkinglot_web;

import java.util.List;
import java.util.Locale;
import java.util.Map;

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
    public String showHome() {
        return "index";
    }

    @GetMapping("/login")
    public String showLogin(HttpSession session) {
        if (isAuthenticated(session)) {
            return redirectToOwnDashboard(session);
        }
        return "login";
    }

    @GetMapping("/register")
    public String showRegister(HttpSession session) {
        if (isAuthenticated(session)) {
            return redirectToOwnDashboard(session);
        }
        return "register";
    }

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

    @PostMapping("/process-login")
    public String processLogin(@RequestParam("email") String email,
                               @RequestParam("password") String password,
                               HttpSession session) {
        try {
            if (parkingApplicationService.loginDriver(email, password)) {
                String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
                String role = parkingApplicationService.getUserRole(normalizedEmail);
                session.setAttribute("userEmail", normalizedEmail);
                session.setAttribute("userRole", role);
                return "redirect:" + parkingApplicationService.getDashboardPathForRole(role);
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

    @GetMapping("/dashboard")
    public String viewDashboard(HttpSession session) {
        if (!isAuthenticated(session)) {
            return "redirect:/login";
        }
        return redirectToOwnDashboard(session);
    }

    @GetMapping("/dashboard/driver")
    public String viewDriverDashboard(HttpSession session, Model model) {
        if (!isAuthenticated(session)) {
            return "redirect:/login";
        }
        if (!hasRole(session, ParkingApplicationService.ROLE_DRIVER)) {
            return redirectToOwnDashboard(session);
        }

        model.addAttribute("systemName", "Driver Portal");
        model.addAttribute("welcomeMessage", "Welcome, " + session.getAttribute("userEmail") + "!");
        return "dashboard";
    }

    @GetMapping("/dashboard/admin")
    public String viewAdminDashboard(HttpSession session, Model model) {
        if (!isAuthenticated(session)) {
            return "redirect:/login";
        }
        if (!hasRole(session, ParkingApplicationService.ROLE_ADMIN)) {
            return redirectToOwnDashboard(session);
        }

        model.addAttribute("systemName", "Admin Control Room");
        model.addAttribute("welcomeMessage", "Welcome, " + session.getAttribute("userEmail") + "!");
        return "admin_dashboard";
    }

    @GetMapping("/dashboard/attendant")
    public String viewAttendantDashboard(HttpSession session, Model model) {
        if (!isAuthenticated(session)) {
            return "redirect:/login";
        }
        if (!hasRole(session, ParkingApplicationService.ROLE_ATTENDANT)) {
            return redirectToOwnDashboard(session);
        }

        model.addAttribute("systemName", "Attendant Desk");
        model.addAttribute("welcomeMessage", "Welcome, " + session.getAttribute("userEmail") + "!");
        model.addAttribute("recentCases", parkingApplicationService.getRecentLostTicketCases(10));
        return "attendant_dashboard";
    }

    @GetMapping("/dashboard/security")
    public String viewSecurityDashboard(HttpSession session, Model model) {
        if (!isAuthenticated(session)) {
            return "redirect:/login";
        }
        if (!hasRole(session, ParkingApplicationService.ROLE_SECURITY_GUARD)) {
            return redirectToOwnDashboard(session);
        }

        model.addAttribute("systemName", "Security Operations");
        model.addAttribute("welcomeMessage", "Welcome, " + session.getAttribute("userEmail") + "!");
        model.addAttribute("recentGateOverrides", parkingApplicationService.getRecentGateOverrides(10));
        return "security_dashboard";
    }

    @GetMapping("/spots")
    public String viewSpots(HttpSession session, Model model) {
        if (!isAuthenticated(session)) {
            return "redirect:/login";
        }
        if (!hasRole(session, ParkingApplicationService.ROLE_DRIVER)) {
            return redirectToOwnDashboard(session);
        }

        model.addAttribute("floors", parkingApplicationService.getFloors());
        model.addAttribute("vehicleTypes", parkingApplicationService.getSupportedVehicleTypes());
        return "spots";
    }

    @PostMapping("/book")
    public String bookSpot(@RequestParam("spotId") String spotId,
                           @RequestParam(value = "vehicleId", required = false) String vehicleId,
                           HttpSession session) {
        if (!isAuthenticated(session)) {
            return "redirect:/login";
        }
        if (!hasRole(session, ParkingApplicationService.ROLE_DRIVER)) {
            return redirectToOwnDashboard(session);
        }

        String userEmail = (String) session.getAttribute("userEmail");
        try {
            boolean booked = parkingApplicationService.bookSpot(spotId, userEmail, vehicleId);
            if (!booked) {
                return "redirect:/spots?bookError=unavailable";
            }
            return "redirect:/spots";
        } catch (IllegalArgumentException ex) {
            return "redirect:/spots?bookError=validation";
        }
    }

    @PostMapping("/checkout")
    public String processCart(@RequestParam(value = "spotIds", required = false) List<String> spotIds,
                              @RequestParam(value = "vehicleIds", required = false) List<String> vehicleIds,
                              @RequestParam(value = "vehicleType", required = false) String vehicleType,
                              @RequestParam("hours") int hours,
                              HttpSession session,
                              Model model) {
        if (!isAuthenticated(session)) {
            return "redirect:/login";
        }
        if (!hasRole(session, ParkingApplicationService.ROLE_DRIVER)) {
            return redirectToOwnDashboard(session);
        }

        try {
            ParkingApplicationService.CheckoutSummary checkoutSummary = parkingApplicationService
                    .buildCheckoutSummary(spotIds, vehicleIds, vehicleType, hours);

            model.addAttribute("selectedSpots", checkoutSummary.getSelectedSpots());
            model.addAttribute("hours", checkoutSummary.getHours());
            model.addAttribute("fee", checkoutSummary.getFee());
            model.addAttribute("spotCount", checkoutSummary.getSpotCount());
            model.addAttribute("vehicleType", checkoutSummary.getVehicleType());
            model.addAttribute("selections", checkoutSummary.getSelections());

            return "checkout";
        } catch (IllegalArgumentException ex) {
            return "redirect:/spots?cartError=validation";
        }
    }

    @PostMapping("/payment")
    public String showPaymentForm(@RequestParam(value = "spotIds", required = false) List<String> spotIds,
                                  @RequestParam(value = "vehicleIds", required = false) List<String> vehicleIds,
                                  @RequestParam(value = "vehicleType", required = false) String vehicleType,
                                  @RequestParam("hours") int hours,
                                  HttpSession session,
                                  Model model) {
        if (!isAuthenticated(session)) {
            return "redirect:/login";
        }
        if (!hasRole(session, ParkingApplicationService.ROLE_DRIVER)) {
            return redirectToOwnDashboard(session);
        }

        try {
            ParkingApplicationService.CheckoutSummary checkoutSummary = parkingApplicationService
                    .buildCheckoutSummary(spotIds, vehicleIds, vehicleType, hours);

            model.addAttribute("selectedSpots", checkoutSummary.getSelectedSpots());
            model.addAttribute("hours", checkoutSummary.getHours());
            model.addAttribute("fee", checkoutSummary.getFee());
            model.addAttribute("spotCount", checkoutSummary.getSpotCount());
            model.addAttribute("vehicleType", checkoutSummary.getVehicleType());
            model.addAttribute("selections", checkoutSummary.getSelections());

            return "payment";
        } catch (IllegalArgumentException ex) {
            return "redirect:/spots?cartError=validation";
        }
    }

    @PostMapping("/pay")
    public String finalizePayment(@RequestParam(value = "spotIds", required = false) List<String> spotIds,
                                  @RequestParam(value = "vehicleIds", required = false) List<String> vehicleIds,
                                  @RequestParam(value = "vehicleType", required = false) String vehicleType,
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
        if (!isAuthenticated(session)) {
            return "redirect:/login";
        }
        if (!hasRole(session, ParkingApplicationService.ROLE_DRIVER)) {
            return redirectToOwnDashboard(session);
        }

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
            boolean success = parkingApplicationService.finalizePayment(spotIds, vehicleIds, vehicleType, hours,
                    paymentRequest, userEmail);
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
        if (!isAuthenticated(session)) {
            return "redirect:/login";
        }
        if (!hasRole(session, ParkingApplicationService.ROLE_DRIVER)) {
            return redirectToOwnDashboard(session);
        }
        return "success";
    }

    @GetMapping("/bookings")
    public String viewBookings(HttpSession session, Model model) {
        if (!isAuthenticated(session)) {
            return "redirect:/login";
        }
        if (!hasRole(session, ParkingApplicationService.ROLE_DRIVER)) {
            return redirectToOwnDashboard(session);
        }

        String userEmail = (String) session.getAttribute("userEmail");
        List<Map<String, String>> tickets = parkingApplicationService.getTicketsForUser(userEmail);
        model.addAttribute("tickets", tickets);
        return "bookings";
    }

    @GetMapping("/security/violation/report")
    public String showViolationForm(HttpSession session, Model model) {
        if (!isAuthenticated(session)) {
            return "redirect:/login";
        }
        if (!hasRole(session, ParkingApplicationService.ROLE_SECURITY_GUARD)) {
            return redirectToOwnDashboard(session);
        }

        model.addAttribute("violations", parkingApplicationService.getAllViolations());
        return "report_violation";
    }

    @PostMapping("/security/violation/report")
    public String submitViolation(@RequestParam("vehicleId") String vehicleId,
                                  @RequestParam(value = "spotId", required = false) String spotId,
                                  @RequestParam("description") String description,
                                  HttpSession session,
                                  Model model) {
        if (!isAuthenticated(session)) {
            return "redirect:/login";
        }
        if (!hasRole(session, ParkingApplicationService.ROLE_SECURITY_GUARD)) {
            return redirectToOwnDashboard(session);
        }

        String userEmail = (String) session.getAttribute("userEmail");
        try {
            String violationId = parkingApplicationService.reportParkingViolation(vehicleId, spotId, description, userEmail);
            if (violationId == null) {
                model.addAttribute("errorMessage", "Violation report could not be saved.");
            } else {
                model.addAttribute("successMessage", "Violation reported successfully. Ref: " + violationId);
            }
        } catch (IllegalArgumentException ex) {
            model.addAttribute("errorMessage", ex.getMessage());
        }

        model.addAttribute("violations", parkingApplicationService.getAllViolations());
        return "report_violation";
    }

    @GetMapping("/security/gate-override")
    public String showGateOverrideForm(HttpSession session, Model model) {
        if (!isAuthenticated(session)) {
            return "redirect:/login";
        }
        if (!hasRole(session, ParkingApplicationService.ROLE_SECURITY_GUARD)) {
            return redirectToOwnDashboard(session);
        }

        model.addAttribute("gateOverrides", parkingApplicationService.getRecentGateOverrides(30));
        return "gate_override";
    }

    @PostMapping("/security/gate-override")
    public String submitGateOverride(@RequestParam("gateId") String gateId,
                                     @RequestParam("action") String action,
                                     @RequestParam("reason") String reason,
                                     HttpSession session,
                                     Model model) {
        if (!isAuthenticated(session)) {
            return "redirect:/login";
        }
        if (!hasRole(session, ParkingApplicationService.ROLE_SECURITY_GUARD)) {
            return redirectToOwnDashboard(session);
        }

        String userEmail = (String) session.getAttribute("userEmail");
        try {
            String logId = parkingApplicationService.manualGateOverride(gateId, action, reason, userEmail);
            if (logId == null) {
                model.addAttribute("errorMessage", "Gate override could not be recorded.");
            } else {
                model.addAttribute("successMessage", "Gate override logged. Ref: " + logId);
            }
        } catch (IllegalArgumentException ex) {
            model.addAttribute("errorMessage", ex.getMessage());
        }

        model.addAttribute("gateOverrides", parkingApplicationService.getRecentGateOverrides(30));
        return "gate_override";
    }

    @GetMapping("/attendant/lost-ticket")
    public String showLostTicketForm(HttpSession session, Model model) {
        if (!isAuthenticated(session)) {
            return "redirect:/login";
        }
        if (!hasRole(session, ParkingApplicationService.ROLE_ATTENDANT)) {
            return redirectToOwnDashboard(session);
        }

        model.addAttribute("recentCases", parkingApplicationService.getRecentLostTicketCases(30));
        return "lost_ticket";
    }

    @PostMapping("/attendant/lost-ticket")
    public String submitLostTicket(@RequestParam(value = "ticketId", required = false) String ticketId,
                                   @RequestParam(value = "userEmail", required = false) String userEmail,
                                   @RequestParam(value = "spotId", required = false) String spotId,
                                   @RequestParam(value = "vehicleId", required = false) String vehicleId,
                                   @RequestParam(value = "notes", required = false) String notes,
                                   HttpSession session,
                                   Model model) {
        if (!isAuthenticated(session)) {
            return "redirect:/login";
        }
        if (!hasRole(session, ParkingApplicationService.ROLE_ATTENDANT)) {
            return redirectToOwnDashboard(session);
        }

        String handledBy = (String) session.getAttribute("userEmail");
        try {
            ParkingApplicationService.LostTicketResult result = parkingApplicationService.handleLostTicket(
                    ticketId,
                    userEmail,
                    spotId,
                    vehicleId,
                    notes,
                    handledBy);
            model.addAttribute("result", result);
            if (!result.isSuccess()) {
                model.addAttribute("errorMessage", result.getMessage());
            } else {
                model.addAttribute("successMessage", result.getMessage() + " Ref: " + result.getCaseId());
            }
        } catch (IllegalArgumentException ex) {
            model.addAttribute("errorMessage", ex.getMessage());
        }

        model.addAttribute("recentCases", parkingApplicationService.getRecentLostTicketCases(30));
        return "lost_ticket";
    }

    @GetMapping("/admin/incidents")
    public String viewAdminIncidents(HttpSession session, Model model) {
        if (!isAuthenticated(session)) {
            return "redirect:/login";
        }
        if (!hasRole(session, ParkingApplicationService.ROLE_ADMIN)) {
            return redirectToOwnDashboard(session);
        }

        populateAdminIncidentModel(model);
        return "admin_incidents";
    }

    @PostMapping("/admin/incidents/session-search")
    public String findActiveSessionByVehicle(@RequestParam("vehicleId") String vehicleId,
                                             HttpSession session,
                                             Model model) {
        if (!isAuthenticated(session)) {
            return "redirect:/login";
        }
        if (!hasRole(session, ParkingApplicationService.ROLE_ADMIN)) {
            return redirectToOwnDashboard(session);
        }

        populateAdminIncidentModel(model);
        model.addAttribute("activeSessionLookupVehicle", vehicleId == null ? "" : vehicleId.trim().toUpperCase(Locale.ROOT));
        try {
            Map<String, String> activeSession = parkingApplicationService.findActiveSessionByVehicle(vehicleId);
            model.addAttribute("activeSessionLookupResult", activeSession);
        } catch (IllegalArgumentException ex) {
            model.addAttribute("activeSessionLookupError", ex.getMessage());
        }
        return "admin_incidents";
    }

    private void populateAdminIncidentModel(Model model) {
        model.addAttribute("violations", parkingApplicationService.getAllViolations());
        model.addAttribute("gateOverrides", parkingApplicationService.getRecentGateOverrides(50));
        model.addAttribute("lostTicketCases", parkingApplicationService.getRecentLostTicketCases(50));
        model.addAttribute("activeSessions", parkingApplicationService.getActiveParkingSessions(100));
    }

    private boolean isAuthenticated(HttpSession session) {
        return session.getAttribute("userEmail") != null;
    }

    private boolean hasRole(HttpSession session, String expectedRole) {
        String actualRole = getSessionRole(session);
        return expectedRole.equalsIgnoreCase(actualRole);
    }

    private String getSessionRole(HttpSession session) {
        Object roleObject = session.getAttribute("userRole");
        if (roleObject == null) {
            return ParkingApplicationService.ROLE_DRIVER;
        }
        return roleObject.toString().trim().toUpperCase(Locale.ROOT);
    }

    private String redirectToOwnDashboard(HttpSession session) {
        return "redirect:" + parkingApplicationService.getDashboardPathForRole(getSessionRole(session));
    }
}