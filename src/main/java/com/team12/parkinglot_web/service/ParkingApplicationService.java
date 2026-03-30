package com.team12.parkinglot_web.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;

import parkinglot.core.ParkingFloor;
import parkinglot.core.ParkingLot;
import parkinglot.db.DatabaseHelper;
import parkinglot.spots.ParkingSpot;

@Service
public class ParkingApplicationService {

    private static final String LOT_ADDRESS = "123 Tech Park, Bengaluru";
    private static final double HOURLY_RATE = 50.0;

    public boolean registerDriver(String firstName, String lastName, String email, String password) {
        return DatabaseHelper.registerDriver(firstName + " " + lastName, email, password);
    }

    public boolean loginDriver(String email, String password) {
        return DatabaseHelper.loginDriver(email, password);
    }

    public List<ParkingFloor> getFloors() {
        return getLot().getFloors();
    }

    public CheckoutSummary buildCheckoutSummary(String selectedSpots, int hours) {
        List<String> normalizedSpots = parseSpotIds(selectedSpots);
        double totalFee = normalizedSpots.size() * hours * HOURLY_RATE;
        return new CheckoutSummary(String.join(",", normalizedSpots), hours, normalizedSpots.size(), totalFee);
    }

    public void bookSpot(String spotId, String userEmail) {
        ParkingLot lot = getLot();
        for (ParkingFloor floor : lot.getFloors()) {
            for (ParkingSpot spot : floor.getSpots()) {
                if (spot.getId().equals(spotId) && spot.isFree()) {
                    spot.book(1);
                    floor.updateDisplay();

                    String ticketId = generateTicketId();
                    long expiryTimeMillis = System.currentTimeMillis() + (1 * 60 * 60 * 1000L);
                    DatabaseHelper.saveTicket(ticketId, userEmail, spotId, "RESERVED", null, null, expiryTimeMillis);
                    return;
                }
            }
        }
    }

    public void finalizePayment(String selectedSpots, int hours, String paymentMethod, String bankCode, String userEmail) {
        ParkingLot lot = getLot();
        List<String> spotIds = parseSpotIds(selectedSpots);

        for (String spotId : spotIds) {
            for (ParkingFloor floor : lot.getFloors()) {
                for (ParkingSpot spot : floor.getSpots()) {
                    if (spot.getId().equals(spotId) && spot.isFree()) {
                        spot.book(hours);
                        floor.updateDisplay();

                        String ticketId = generateTicketId();
                        long expiryTimeMillis = System.currentTimeMillis() + (hours * 60 * 60 * 1000L);
                        DatabaseHelper.saveTicket(ticketId, userEmail, spotId, "PAID", paymentMethod, bankCode, expiryTimeMillis);
                        break;
                    }
                }
            }
        }
    }

    public List<Map<String, String>> getTicketsForUser(String email) {
        return DatabaseHelper.getTicketsForUser(email);
    }

    private ParkingLot getLot() {
        return ParkingLot.getInstance(LOT_ADDRESS);
    }

    private String generateTicketId() {
        return "TKT-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
    }

    private List<String> parseSpotIds(String selectedSpots) {
        List<String> spotIds = new ArrayList<>();
        if (selectedSpots == null || selectedSpots.trim().isEmpty()) {
            return spotIds;
        }

        String[] rawSpots = selectedSpots.split(",");
        for (String spot : rawSpots) {
            String trimmed = spot.trim();
            if (!trimmed.isEmpty()) {
                spotIds.add(trimmed);
            }
        }
        return spotIds;
    }

    public static class CheckoutSummary {
        private final String selectedSpots;
        private final int hours;
        private final int spotCount;
        private final double fee;

        public CheckoutSummary(String selectedSpots, int hours, int spotCount, double fee) {
            this.selectedSpots = selectedSpots;
            this.hours = hours;
            this.spotCount = spotCount;
            this.fee = fee;
        }

        public String getSelectedSpots() {
            return selectedSpots;
        }

        public int getHours() {
            return hours;
        }

        public int getSpotCount() {
            return spotCount;
        }

        public double getFee() {
            return fee;
        }
    }
}
