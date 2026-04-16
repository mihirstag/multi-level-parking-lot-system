package com.team12.parkinglot_web.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import com.team12.parkinglot_web.payment.PaymentProcessorAdapter;
import com.team12.parkinglot_web.payment.PaymentRequest;

import parkinglot.core.ParkingFloor;
import parkinglot.core.ParkingLot;
import parkinglot.db.DatabaseHelper;
import parkinglot.spots.ParkingSpot;

@Service
public class ParkingApplicationService {

    private static final String LOT_ADDRESS = "123 Tech Park, Bengaluru";
    private static final double HOURLY_RATE = 50.0;
    private static final Pattern NAME_PATTERN = Pattern.compile("^[A-Za-z][A-Za-z\\s'-]{0,39}$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");
    private static final Pattern SPOT_ID_PATTERN = Pattern.compile("^[A-Za-z0-9-]{3,20}$");

    private final Object bookingLock = new Object();
    private final PaymentProcessorAdapter paymentProcessorAdapter;

    public ParkingApplicationService(PaymentProcessorAdapter paymentProcessorAdapter) {
        this.paymentProcessorAdapter = paymentProcessorAdapter;
    }

    public boolean registerDriver(String firstName, String lastName, String email, String password) {
        validateName(firstName, "First name");
        validateName(lastName, "Last name");
        validateEmail(email);
        validatePassword(password);
        return DatabaseHelper.registerDriver(firstName + " " + lastName, email.trim().toLowerCase(), password);
    }

    public boolean loginDriver(String email, String password) {
        validateEmail(email);
        if (password == null || password.trim().isEmpty()) {
            throw new IllegalArgumentException("Password is required.");
        }
        return DatabaseHelper.loginDriver(email.trim().toLowerCase(), password);
    }

    public List<ParkingFloor> getFloors() {
        return getLot().getFloors();
    }

    public CheckoutSummary buildCheckoutSummary(String selectedSpots, int hours) {
        validateHours(hours);
        List<String> normalizedSpots = parseSpotIds(selectedSpots);
        if (normalizedSpots.isEmpty()) {
            throw new IllegalArgumentException("Select at least one parking spot.");
        }

        double totalFee = normalizedSpots.size() * hours * HOURLY_RATE;
        return new CheckoutSummary(String.join(",", normalizedSpots), hours, normalizedSpots.size(), totalFee);
    }

    public boolean bookSpot(String spotId, String userEmail) {
        validateSpotId(spotId);
        validateEmail(userEmail);

        synchronized (bookingLock) {
            ReservationTarget target = findReservationTarget(spotId);
            if (target == null || !target.spot.isFree()) {
                return false;
            }

            target.spot.book(1);
            target.floor.updateDisplay();

            String ticketId = generateTicketId();
            long expiryTimeMillis = System.currentTimeMillis() + (60 * 60 * 1000L);
            DatabaseHelper.saveTicket(ticketId, userEmail, spotId, "RESERVED", null, null, expiryTimeMillis);
            return true;
        }
    }

    public boolean finalizePayment(String selectedSpots, int hours, PaymentRequest paymentRequest, String userEmail) {
        validateEmail(userEmail);
        validateHours(hours);

        List<String> spotIds = parseSpotIds(selectedSpots);
        if (spotIds.isEmpty()) {
            throw new IllegalArgumentException("Select at least one parking spot.");
        }

        double totalFee = spotIds.size() * hours * HOURLY_RATE;

        synchronized (bookingLock) {
            List<ReservationTarget> targets = resolveAvailableTargets(spotIds);
            if (targets.size() != spotIds.size()) {
                return false;
            }

            if (!paymentProcessorAdapter.processPayment(paymentRequest, userEmail, totalFee)) {
                return false;
            }

            long expiryTimeMillis = System.currentTimeMillis() + (hours * 60 * 60 * 1000L);
            String paymentMethod = paymentRequest.getNormalizedPaymentMethod();
            String bankCode = paymentRequest.getBankCode().isEmpty() ? null : paymentRequest.getBankCode();

            for (ReservationTarget target : targets) {
                target.spot.book(hours);
                DatabaseHelper.saveTicket(generateTicketId(), userEmail, target.spot.getId(), "PAID", paymentMethod, bankCode,
                        expiryTimeMillis);
            }

            refreshDisplays(targets);
            return true;
        }
    }

    public List<Map<String, String>> getTicketsForUser(String email) {
        validateEmail(email);
        return DatabaseHelper.getTicketsForUser(email.trim().toLowerCase());
    }

    private ParkingLot getLot() {
        return ParkingLot.getInstance(LOT_ADDRESS);
    }

    private String generateTicketId() {
        return "TKT-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
    }

    private List<String> parseSpotIds(String selectedSpots) {
        LinkedHashSet<String> uniqueIds = new LinkedHashSet<>();
        if (selectedSpots == null || selectedSpots.trim().isEmpty()) {
            return new ArrayList<>();
        }

        String[] rawSpots = selectedSpots.split(",");
        for (String spot : rawSpots) {
            String trimmed = spot.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            validateSpotId(trimmed);
            uniqueIds.add(trimmed);
        }

        return new ArrayList<>(uniqueIds);
    }

    private ReservationTarget findReservationTarget(String spotId) {
        ParkingLot lot = getLot();
        for (ParkingFloor floor : lot.getFloors()) {
            for (ParkingSpot spot : floor.getSpots()) {
                if (spotId.equals(spot.getId())) {
                    return new ReservationTarget(floor, spot);
                }
            }
        }
        return null;
    }

    private List<ReservationTarget> resolveAvailableTargets(List<String> spotIds) {
        List<ReservationTarget> targets = new ArrayList<>();
        for (String spotId : spotIds) {
            ReservationTarget target = findReservationTarget(spotId);
            if (target == null || !target.spot.isFree()) {
                return new ArrayList<>();
            }
            targets.add(target);
        }
        return targets;
    }

    private void refreshDisplays(List<ReservationTarget> targets) {
        Set<ParkingFloor> touchedFloors = new HashSet<>();
        for (ReservationTarget target : targets) {
            touchedFloors.add(target.floor);
        }

        for (ParkingFloor floor : touchedFloors) {
            floor.updateDisplay();
        }
    }

    private void validateName(String value, String label) {
        if (value == null || !NAME_PATTERN.matcher(value.trim()).matches()) {
            throw new IllegalArgumentException(label + " is invalid.");
        }
    }

    private void validateEmail(String email) {
        if (email == null || !EMAIL_PATTERN.matcher(email.trim()).matches()) {
            throw new IllegalArgumentException("Email is invalid.");
        }
    }

    private void validatePassword(String password) {
        if (password == null || password.length() < 8) {
            throw new IllegalArgumentException("Password must be at least 8 characters.");
        }
    }

    private void validateHours(int hours) {
        if (hours < 1 || hours > 24) {
            throw new IllegalArgumentException("Hours must be between 1 and 24.");
        }
    }

    private void validateSpotId(String spotId) {
        if (spotId == null || !SPOT_ID_PATTERN.matcher(spotId.trim()).matches()) {
            throw new IllegalArgumentException("Invalid spot identifier.");
        }
    }

    private static class ReservationTarget {
        private final ParkingFloor floor;
        private final ParkingSpot spot;

        private ReservationTarget(ParkingFloor floor, ParkingSpot spot) {
            this.floor = floor;
            this.spot = spot;
        }
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
