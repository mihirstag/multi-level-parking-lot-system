package com.team12.parkinglot_web; 

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import parkinglot.core.ParkingFloor;
import parkinglot.core.ParkingLot;
import parkinglot.enums.SpotType;
import parkinglot.spots.SpotFactory;
import parkinglot.spots.ParkingSpot;

@Controller
public class ParkingController {

    public ParkingController() {
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
    public String viewDashboard(Model model) {
        model.addAttribute("systemName", "Multi-Level Parking Lot System");
        model.addAttribute("welcomeMessage", "Welcome, Driver! Please book your spot.");
        return "dashboard"; 
    }

    @GetMapping("/spots")
    public String viewSpots(Model model) {
        ParkingLot lot = ParkingLot.getInstance("123 Tech Park, Bengaluru");
        model.addAttribute("floors", lot.getFloors());
        return "spots";
    }

    // NEW METHOD: Handles the booking request
    // Notice the ("spotId") added inside the parenthesis here!
    // UPDATED METHOD: Redirects to checkout instead of just refreshing
    @PostMapping("/book")
    public String bookSpot(@RequestParam("spotId") String spotId) {
        ParkingLot lot = ParkingLot.getInstance("123 Tech Park, Bengaluru");
        
        for (ParkingFloor floor : lot.getFloors()) {
            for (ParkingSpot spot : floor.getSpots()) {
                if (spot.getId().equals(spotId) && spot.isFree()) {
                    spot.book(); // Updates status to RESERVED
                    floor.updateDisplay(); 
                    // Redirect to checkout with the spot ID
                    return "redirect:/checkout?spotId=" + spotId; 
                }
            }
        }
        return "redirect:/spots"; 
    }

    // NEW METHOD: Shows the checkout page
    @GetMapping("/checkout")
    public String viewCheckout(@RequestParam("spotId") String spotId, Model model) {
        model.addAttribute("spotId", spotId);
        model.addAttribute("fee", 50.0); // Using a static ₹50 fee for now
        return "checkout";
    }

    // NEW METHOD: Processes payment and Auto-Releases the spot
    @PostMapping("/pay")
    public String processPayment(@RequestParam("spotId") String spotId) {
        ParkingLot lot = ParkingLot.getInstance("123 Tech Park, Bengaluru");
        
        // Find the spot and release it (simulating successful payment)
        for (ParkingFloor floor : lot.getFloors()) {
            for (ParkingSpot spot : floor.getSpots()) {
                if (spot.getId().equals(spotId)) {
                    spot.release(); // Triggers Auto-Release System Logic
                    floor.updateDisplay();
                }
            }
        }
        // Send them back to the dashboard with a success flag
        return "redirect:/?payment=success";
    }
}