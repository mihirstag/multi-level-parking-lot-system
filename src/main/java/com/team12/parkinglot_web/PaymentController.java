package com.team12.parkinglot_web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import parkinglot.transactions.Payment;
import parkinglot.transactions.CreditCardPayment;
import parkinglot.db.DatabaseHelper;
import parkinglot.core.ParkingFloor;
import parkinglot.core.ParkingLot;
import parkinglot.spots.ParkingSpot;
import java.util.UUID;

@RestController
public class PaymentController {

    @PostMapping("/process-payment")
    public PaymentResponse processPayment(@RequestBody PaymentRequest request) {
        try {
            String[] spotsArray = request.spots.split(",");
            
            for (String spotId : spotsArray) {
                spotId = spotId.trim();
                
                // Clear reservation so it becomes available again (green)
                DatabaseHelper.clearReservation(spotId);

                // Also update the in-memory spot object so it shows available immediately
                ParkingLot lot = ParkingLot.getInstance("123 Tech Park, Bengaluru");
                for (ParkingFloor floor : lot.getFloors()) {
                    for (ParkingSpot spot : floor.getSpots()) {
                        if (spot.getId().equals(spotId)) {
                            spot.release();
                        }
                    }
                }

                // Record the payment against a new ticket ID (for history)
                String ticketId = "TKT-" + java.util.UUID.randomUUID().toString().substring(0, 6).toUpperCase();
                DatabaseHelper.savePayment(ticketId, Double.parseDouble(request.fee) / spotsArray.length);
                System.out.println("✓ Payment processed for spot " + spotId + " (ticket " + ticketId + ")");
            }
            
            return new PaymentResponse(true, "✓ Payment successful! Your parking spots are confirmed.");
        } catch (Exception e) {
            e.printStackTrace();
            return new PaymentResponse(false, "❌ Payment failed: " + e.getMessage());
        }
    }
}

class PaymentRequest {
    public String spots;
    public String hours;
    public String fee;
    public String paymentMethod;
    public java.util.Map<String, Object> paymentDetails;
}

class PaymentResponse {
    public boolean success;
    public String message;
    
    public PaymentResponse(boolean success, String message) {
        this.success = success;
        this.message = message;
    }
}