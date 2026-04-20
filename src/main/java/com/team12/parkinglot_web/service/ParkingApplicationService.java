package com.team12.parkinglot_web.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
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
import parkinglot.enums.SpotStatus;
import parkinglot.enums.SpotType;
import parkinglot.spots.ParkingSpot;

@Service
public class ParkingApplicationService {

    private static final String LOT_ADDRESS = "123 Tech Park, Bengaluru";
    private static final double HOURLY_RATE = 50.0;
    public static final String ROLE_DRIVER = DatabaseHelper.ROLE_DRIVER;
    public static final String ROLE_ADMIN = DatabaseHelper.ROLE_ADMIN;
    public static final String ROLE_ATTENDANT = DatabaseHelper.ROLE_ATTENDANT;
    public static final String ROLE_SECURITY_GUARD = DatabaseHelper.ROLE_SECURITY_GUARD;
    private static final Pattern NAME_PATTERN = Pattern.compile("^[A-Za-z][A-Za-z\\s'-]{0,39}$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");
    private static final Pattern SPOT_ID_PATTERN = Pattern.compile("^[A-Za-z0-9-]{3,20}$");
    private static final Pattern VEHICLE_ID_PATTERN = Pattern.compile("^[A-Za-z0-9-]{3,20}$");
    private static final Pattern GATE_ID_PATTERN = Pattern.compile("^[A-Za-z0-9-]{2,12}$");
    private static final Pattern TICKET_ID_PATTERN = Pattern.compile("^[A-Za-z0-9-]{4,24}$");

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

    public String getUserRole(String email) {
        validateEmail(email);
        return normalizeRole(DatabaseHelper.getUserRole(email.trim().toLowerCase()));
    }

    public String getDashboardPathForRole(String role) {
        String normalizedRole = normalizeRole(role);
        if (ROLE_ADMIN.equals(normalizedRole)) {
            return "/dashboard/admin";
        }
        if (ROLE_ATTENDANT.equals(normalizedRole)) {
            return "/dashboard/attendant";
        }
        if (ROLE_SECURITY_GUARD.equals(normalizedRole)) {
            return "/dashboard/security";
        }
        return "/dashboard/driver";
    }

    public List<ParkingFloor> getFloors() {
        reconcileLifecycleStates();
        return getLot().getFloors();
    }

    public List<SpotType> getSupportedVehicleTypes() {
        List<SpotType> types = new ArrayList<>();
        for (SpotType type : SpotType.values()) {
            types.add(type);
        }
        return types;
    }

    public CheckoutSummary buildCheckoutSummary(String selectedSpots, int hours) {
        return buildCheckoutSummary(selectedSpots, hours, null);
    }

    public CheckoutSummary buildCheckoutSummary(String selectedSpots, int hours, String vehicleId) {
        List<String> spotIds = parseSpotIds(selectedSpots);
        SpotType inferredType = inferCommonSpotType(spotIds);

        String normalizedVehicleId = safe(vehicleId).toUpperCase(Locale.ROOT);
        if (normalizedVehicleId.isEmpty()) {
            throw new IllegalArgumentException("Vehicle identifier is required for every selected spot.");
        }
        validateVehicleId(normalizedVehicleId);

        List<String> vehicleIds = new ArrayList<>();
        for (int i = 0; i < spotIds.size(); i++) {
            vehicleIds.add(normalizedVehicleId);
        }

        return buildCheckoutSummary(spotIds, vehicleIds, inferredType.name(), hours);
    }

    public CheckoutSummary buildCheckoutSummary(List<String> spotIds,
                                                List<String> vehicleIds,
                                                String vehicleType,
                                                int hours) {
        validateHours(hours);
        SpotType normalizedVehicleType = parseVehicleType(vehicleType);
        List<SpotVehicleSelection> selections = normalizeSelections(spotIds, vehicleIds, normalizedVehicleType);
        double totalFee = selections.size() * hours * HOURLY_RATE;
        return new CheckoutSummary(selections, normalizedVehicleType.name(), hours, totalFee);
    }

    public boolean bookSpot(String spotId, String userEmail) {
        return bookSpot(spotId, userEmail, "UNKNOWN");
    }

    public boolean bookSpot(String spotId, String userEmail, String vehicleId) {
        validateSpotId(spotId);
        validateEmail(userEmail);
        String normalizedVehicleId = safe(vehicleId).toUpperCase(Locale.ROOT);
        if (!"UNKNOWN".equals(normalizedVehicleId)) {
            validateVehicleId(normalizedVehicleId);
        }

        reconcileLifecycleStates();

        synchronized (bookingLock) {
            ReservationTarget target = findReservationTarget(spotId);
            if (target == null || !target.spot.isFree()) {
                return false;
            }

            target.spot.reserve(1);
            target.floor.updateDisplay();

            String ticketId = generateTicketId();
            long expiryTimeMillis = System.currentTimeMillis() + (60 * 60 * 1000L);
            DatabaseHelper.saveTicket(ticketId, userEmail, spotId, normalizedVehicleId, DatabaseHelper.TICKET_STATUS_RESERVED,
                    null, null, expiryTimeMillis);
            return true;
        }
    }

    public boolean finalizePayment(String selectedSpots, int hours, PaymentRequest paymentRequest, String userEmail) {
        return finalizePayment(selectedSpots, hours, paymentRequest, userEmail, "UNKNOWN");
    }

    public boolean finalizePayment(String selectedSpots, int hours, PaymentRequest paymentRequest, String userEmail,
            String vehicleId) {
        List<String> spotIds = parseSpotIds(selectedSpots);
        SpotType inferredType = inferCommonSpotType(spotIds);

        String normalizedVehicleId = safe(vehicleId).toUpperCase(Locale.ROOT);
        if (normalizedVehicleId.isEmpty()) {
            throw new IllegalArgumentException("Vehicle identifier is required for every selected spot.");
        }
        validateVehicleId(normalizedVehicleId);

        List<String> vehicleIds = new ArrayList<>();
        for (int i = 0; i < spotIds.size(); i++) {
            vehicleIds.add(normalizedVehicleId);
        }

        return finalizePayment(spotIds, vehicleIds, inferredType.name(), hours, paymentRequest, userEmail);
    }

    public boolean finalizePayment(List<String> spotIds,
                                   List<String> vehicleIds,
                                   String vehicleType,
                                   int hours,
                                   PaymentRequest paymentRequest,
                                   String userEmail) {
        validateEmail(userEmail);
        validateHours(hours);
        if (paymentRequest == null) {
            throw new IllegalArgumentException("Payment request is required.");
        }

        SpotType normalizedVehicleType = parseVehicleType(vehicleType);
        List<SpotVehicleSelection> selections = normalizeSelections(spotIds, vehicleIds, normalizedVehicleType);

        reconcileLifecycleStates();

        double totalFee = selections.size() * hours * HOURLY_RATE;

        synchronized (bookingLock) {
            List<ReservationTarget> targets = resolveAvailableTargets(selections, normalizedVehicleType);
            if (targets.size() != selections.size()) {
                return false;
            }

            if (!paymentProcessorAdapter.processPayment(paymentRequest, userEmail, totalFee)) {
                return false;
            }

            long expiryTimeMillis = System.currentTimeMillis() + (hours * 60 * 60 * 1000L);
            String paymentMethod = paymentRequest.getNormalizedPaymentMethod();
            String bankCode = paymentRequest.getBankCode().isEmpty() ? null : paymentRequest.getBankCode();
            long entryTimeMillis = System.currentTimeMillis();
            Map<String, String> vehicleBySpot = new HashMap<>();
            for (SpotVehicleSelection selection : selections) {
                vehicleBySpot.put(selection.getSpotId(), selection.getVehicleId());
            }

            for (ReservationTarget target : targets) {
                target.spot.occupy(hours);
                String vehicleIdForSpot = vehicleBySpot.get(target.spot.getId());
                DatabaseHelper.saveTicket(generateTicketId(), userEmail, target.spot.getId(), vehicleIdForSpot,
                        DatabaseHelper.TICKET_STATUS_OCCUPIED, paymentMethod, bankCode, expiryTimeMillis,
                        entryTimeMillis);
            }

            refreshDisplays(targets);
            return true;
        }
    }

    public List<Map<String, String>> getTicketsForUser(String email) {
        validateEmail(email);
        reconcileLifecycleStates();
        return DatabaseHelper.getTicketsForUser(email.trim().toLowerCase());
    }

    public List<Map<String, String>> getActiveParkingSessions(int limit) {
        reconcileLifecycleStates();
        return DatabaseHelper.getActiveParkingSessions(limit);
    }

    public Map<String, String> findActiveSessionByVehicle(String vehicleId) {
        validateVehicleId(vehicleId);
        reconcileLifecycleStates();
        return DatabaseHelper.findActiveSessionByVehicle(vehicleId);
    }

    public String reportParkingViolation(String vehicleId, String spotId, String description, String reportedByEmail) {
        validateEmail(reportedByEmail);
        validateVehicleId(vehicleId);

        String normalizedSpotId = safe(spotId);
        if (!normalizedSpotId.isEmpty()) {
            validateSpotId(normalizedSpotId);
        }

        String normalizedDescription = safe(description);
        validateDescription(normalizedDescription, "Violation description", 8, 300);

        return DatabaseHelper.saveViolation(
                reportedByEmail.trim().toLowerCase(Locale.ROOT),
                vehicleId.trim().toUpperCase(Locale.ROOT),
                normalizedSpotId,
                normalizedDescription);
    }

    public String manualGateOverride(String gateId, String action, String reason, String overriddenByEmail) {
        validateEmail(overriddenByEmail);

        String normalizedGateId = safe(gateId).toUpperCase(Locale.ROOT);
        String normalizedAction = safe(action).toUpperCase(Locale.ROOT);
        String normalizedReason = safe(reason);

        validateGateId(normalizedGateId);
        if (!"OPEN".equals(normalizedAction) && !"CLOSE".equals(normalizedAction) && !"HOLD".equals(normalizedAction)) {
            throw new IllegalArgumentException("Invalid gate override action.");
        }
        validateDescription(normalizedReason, "Override reason", 5, 250);

        return DatabaseHelper.saveGateOverrideLog(
                normalizedGateId,
                normalizedAction,
                normalizedReason,
                overriddenByEmail.trim().toLowerCase(Locale.ROOT));
    }

    public LostTicketResult handleLostTicket(String ticketId, String userEmail, String spotId, String vehicleId,
            String notes, String handledByEmail) {
        validateEmail(handledByEmail);

        String normalizedTicketId = safe(ticketId).toUpperCase(Locale.ROOT);
        String normalizedUserEmail = safe(userEmail).toLowerCase(Locale.ROOT);
        String normalizedSpotId = safe(spotId).toUpperCase(Locale.ROOT);
        String normalizedVehicleId = safe(vehicleId).toUpperCase(Locale.ROOT);
        String normalizedNotes = safe(notes);

        if (normalizedTicketId.isEmpty() && (normalizedUserEmail.isEmpty() || normalizedSpotId.isEmpty())) {
            throw new IllegalArgumentException("Provide ticket id or both user email and spot id.");
        }

        if (!normalizedTicketId.isEmpty()) {
            validateTicketId(normalizedTicketId);
        }
        if (!normalizedUserEmail.isEmpty()) {
            validateEmail(normalizedUserEmail);
        }
        if (!normalizedSpotId.isEmpty()) {
            validateSpotId(normalizedSpotId);
        }
        if (!normalizedVehicleId.isEmpty()) {
            validateVehicleId(normalizedVehicleId);
        }

        synchronized (bookingLock) {
            Map<String, String> ticket = !normalizedTicketId.isEmpty()
                    ? DatabaseHelper.getTicketById(normalizedTicketId)
                    : DatabaseHelper.findLatestTicketByUserAndSpot(normalizedUserEmail, normalizedSpotId);

            if (ticket == null) {
                return LostTicketResult.failure("No matching ticket found.");
            }

            String status = safe(ticket.get("status")).toUpperCase(Locale.ROOT);
            if ("LOST_TICKET".equals(status) || "CLOSED".equals(status) || "EXPIRED".equals(status)) {
                return LostTicketResult.failure("Ticket is already closed and cannot be marked lost.");
            }

            String resolvedTicketId = safe(ticket.get("ticketId")).toUpperCase(Locale.ROOT);
            String resolvedSpotId = safe(ticket.get("spotId")).toUpperCase(Locale.ROOT);
            String resolvedUserEmail = safe(ticket.get("userEmail")).toLowerCase(Locale.ROOT);

            releaseSpotIfReserved(resolvedSpotId);

            boolean updated = DatabaseHelper.updateTicketStatusByTicketId(resolvedTicketId, "LOST_TICKET");
            if (!updated) {
                return LostTicketResult.failure("Failed to update ticket status.");
            }

            String caseId = DatabaseHelper.saveLostTicketCase(
                    resolvedTicketId,
                    resolvedSpotId,
                    resolvedUserEmail,
                    normalizedVehicleId,
                    handledByEmail.trim().toLowerCase(Locale.ROOT),
                    normalizedNotes);

            if (caseId == null) {
                return LostTicketResult.failure("Ticket status updated, but audit case could not be saved.");
            }

            return LostTicketResult.success(caseId, resolvedTicketId, resolvedSpotId, resolvedUserEmail);
        }
    }

    public List<Map<String, String>> getAllViolations() {
        return DatabaseHelper.getViolations();
    }

    public List<Map<String, String>> getRecentGateOverrides(int limit) {
        return DatabaseHelper.getGateOverrideLogs(limit);
    }

    public List<Map<String, String>> getRecentLostTicketCases(int limit) {
        return DatabaseHelper.getLostTicketCases(limit);
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
            String normalizedSpotId = trimmed.toUpperCase(Locale.ROOT);
            validateSpotId(normalizedSpotId);
            uniqueIds.add(normalizedSpotId);
        }

        return new ArrayList<>(uniqueIds);
    }

    private List<SpotVehicleSelection> normalizeSelections(List<String> spotIds,
                                                           List<String> vehicleIds,
                                                           SpotType expectedSpotType) {
        if (spotIds == null || vehicleIds == null || spotIds.isEmpty() || vehicleIds.isEmpty()) {
            throw new IllegalArgumentException("Select at least one spot and provide a vehicle for each.");
        }
        if (spotIds.size() != vehicleIds.size()) {
            throw new IllegalArgumentException("Every selected spot must have a corresponding vehicle identifier.");
        }

        LinkedHashSet<String> uniqueSpotIds = new LinkedHashSet<>();
        List<SpotVehicleSelection> selections = new ArrayList<>();
        for (int i = 0; i < spotIds.size(); i++) {
            String normalizedSpotId = safe(spotIds.get(i)).toUpperCase(Locale.ROOT);
            String normalizedVehicleId = safe(vehicleIds.get(i)).toUpperCase(Locale.ROOT);

            validateSpotId(normalizedSpotId);
            validateVehicleId(normalizedVehicleId);

            if (!uniqueSpotIds.add(normalizedSpotId)) {
                throw new IllegalArgumentException("Duplicate spot detected in selection: " + normalizedSpotId);
            }

            ReservationTarget target = findReservationTarget(normalizedSpotId);
            if (target == null) {
                throw new IllegalArgumentException("Spot not found: " + normalizedSpotId);
            }
            if (target.spot.getType() != expectedSpotType) {
                throw new IllegalArgumentException(
                        "Spot " + normalizedSpotId + " is not valid for vehicle type " + expectedSpotType.name() + ".");
            }

            selections.add(new SpotVehicleSelection(normalizedSpotId, normalizedVehicleId));
        }

        return selections;
    }

    private SpotType parseVehicleType(String vehicleType) {
        String normalized = safe(vehicleType).toUpperCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Vehicle type is required.");
        }

        try {
            return SpotType.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Unsupported vehicle type.");
        }
    }

    private SpotType inferCommonSpotType(List<String> spotIds) {
        if (spotIds.isEmpty()) {
            throw new IllegalArgumentException("Select at least one parking spot.");
        }

        SpotType inferred = null;
        for (String spotId : spotIds) {
            ReservationTarget target = findReservationTarget(spotId);
            if (target == null) {
                throw new IllegalArgumentException("Spot not found: " + spotId);
            }

            SpotType currentType = target.spot.getType();
            if (inferred == null) {
                inferred = currentType;
            } else if (inferred != currentType) {
                throw new IllegalArgumentException(
                        "Selected spots span multiple vehicle types. Choose one vehicle type per checkout.");
            }
        }

        return inferred;
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

    private List<ReservationTarget> resolveAvailableTargets(List<SpotVehicleSelection> selections,
                                                            SpotType expectedSpotType) {
        List<ReservationTarget> targets = new ArrayList<>();
        for (SpotVehicleSelection selection : selections) {
            ReservationTarget target = findReservationTarget(selection.getSpotId());
            if (target == null || target.spot.getType() != expectedSpotType || !target.spot.isFree()) {
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

    private void releaseSpotIfReserved(String spotId) {
        if (spotId == null || spotId.isEmpty()) {
            return;
        }

        ReservationTarget target = findReservationTarget(spotId);
        if (target != null && !target.spot.isFree()) {
            target.spot.release();
            target.floor.updateDisplay();
        }
    }

    private void reconcileLifecycleStates() {
        long now = System.currentTimeMillis();
        DatabaseHelper.reconcileExpiredTickets(now);

        ParkingLot lot = getLot();
        for (ParkingFloor floor : lot.getFloors()) {
            boolean floorChanged = false;
            for (ParkingSpot spot : floor.getSpots()) {
                SpotStatus statusBefore = spot.getStatus();
                spot.isFree();
                if (spot.getStatus() == SpotStatus.EXPIRED) {
                    spot.release();
                    floorChanged = true;
                    continue;
                }
                if (statusBefore != spot.getStatus()) {
                    floorChanged = true;
                }
            }
            if (floorChanged) {
                floor.updateDisplay();
            }
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

    private void validateVehicleId(String vehicleId) {
        if (vehicleId == null || !VEHICLE_ID_PATTERN.matcher(vehicleId.trim()).matches()) {
            throw new IllegalArgumentException("Invalid vehicle identifier.");
        }
    }

    private void validateGateId(String gateId) {
        if (gateId == null || !GATE_ID_PATTERN.matcher(gateId.trim()).matches()) {
            throw new IllegalArgumentException("Invalid gate identifier.");
        }
    }

    private void validateTicketId(String ticketId) {
        if (ticketId == null || !TICKET_ID_PATTERN.matcher(ticketId.trim()).matches()) {
            throw new IllegalArgumentException("Invalid ticket identifier.");
        }
    }

    private void validateDescription(String value, String label, int minLength, int maxLength) {
        if (value == null || value.length() < minLength || value.length() > maxLength) {
            throw new IllegalArgumentException(label + " must be between " + minLength + " and " + maxLength + " characters.");
        }
    }

    private String normalizeRole(String role) {
        if (role == null) {
            return ROLE_DRIVER;
        }

        String normalized = role.trim().toUpperCase(Locale.ROOT);
        if (ROLE_ADMIN.equals(normalized) || ROLE_ATTENDANT.equals(normalized)
                || ROLE_SECURITY_GUARD.equals(normalized) || ROLE_DRIVER.equals(normalized)) {
            return normalized;
        }
        return ROLE_DRIVER;
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static class ReservationTarget {
        private final ParkingFloor floor;
        private final ParkingSpot spot;

        private ReservationTarget(ParkingFloor floor, ParkingSpot spot) {
            this.floor = floor;
            this.spot = spot;
        }
    }

    public static class SpotVehicleSelection {
        private final String spotId;
        private final String vehicleId;

        public SpotVehicleSelection(String spotId, String vehicleId) {
            this.spotId = spotId;
            this.vehicleId = vehicleId;
        }

        public String getSpotId() {
            return spotId;
        }

        public String getVehicleId() {
            return vehicleId;
        }
    }

    public static class CheckoutSummary {
        private final String selectedSpots;
        private final int hours;
        private final int spotCount;
        private final double fee;
        private final String vehicleType;
        private final List<SpotVehicleSelection> selections;
        private final String vehicleId;

        public CheckoutSummary(List<SpotVehicleSelection> selections, String vehicleType, int hours, double fee) {
            this.selections = Collections.unmodifiableList(new ArrayList<>(selections));
            this.vehicleType = vehicleType;
            this.hours = hours;
            this.spotCount = this.selections.size();
            this.fee = fee;

            List<String> spotIds = new ArrayList<>();
            for (SpotVehicleSelection selection : this.selections) {
                spotIds.add(selection.getSpotId());
            }
            this.selectedSpots = String.join(",", spotIds);
            this.vehicleId = this.selections.isEmpty() ? "" : this.selections.get(0).getVehicleId();
        }

        public CheckoutSummary(String selectedSpots, int hours, int spotCount, double fee, String vehicleId) {
            this.selectedSpots = selectedSpots;
            this.hours = hours;
            this.spotCount = spotCount;
            this.fee = fee;
            this.vehicleType = "";
            this.selections = Collections.emptyList();
            this.vehicleId = vehicleId;
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

        public String getVehicleType() {
            return vehicleType;
        }

        public List<SpotVehicleSelection> getSelections() {
            return selections;
        }

        public String getVehicleId() {
            return vehicleId;
        }
    }

    public static class LostTicketResult {
        private final boolean success;
        private final String message;
        private final String caseId;
        private final String ticketId;
        private final String spotId;
        private final String userEmail;

        private LostTicketResult(boolean success, String message, String caseId, String ticketId, String spotId,
                String userEmail) {
            this.success = success;
            this.message = message;
            this.caseId = caseId;
            this.ticketId = ticketId;
            this.spotId = spotId;
            this.userEmail = userEmail;
        }

        public static LostTicketResult success(String caseId, String ticketId, String spotId, String userEmail) {
            return new LostTicketResult(true, "Lost ticket case handled successfully.", caseId, ticketId, spotId,
                    userEmail);
        }

        public static LostTicketResult failure(String message) {
            return new LostTicketResult(false, message, null, null, null, null);
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }

        public String getCaseId() {
            return caseId;
        }

        public String getTicketId() {
            return ticketId;
        }

        public String getSpotId() {
            return spotId;
        }

        public String getUserEmail() {
            return userEmail;
        }
    }
}
