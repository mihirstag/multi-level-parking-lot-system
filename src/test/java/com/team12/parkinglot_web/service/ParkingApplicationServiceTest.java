package com.team12.parkinglot_web.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.team12.parkinglot_web.payment.PaymentProcessorAdapter;
import com.team12.parkinglot_web.payment.PaymentRequest;

import parkinglot.core.ParkingFloor;
import parkinglot.core.ParkingLot;
import parkinglot.db.DatabaseHelper;
import parkinglot.enums.SpotType;
import parkinglot.spots.ParkingSpot;

class ParkingApplicationServiceTest {

    private static final Path DB_PATH = Paths.get("parkinglot.db");

    private ParkingApplicationService parkingApplicationService;

    @BeforeEach
    void setUp() throws IOException {
        Files.deleteIfExists(DB_PATH);

        ParkingLot lot = ParkingLot.getInstance("123 Tech Park, Bengaluru");
        lot.getFloors().clear();

        ParkingLotBootstrapService bootstrapService = new ParkingLotBootstrapService();
        bootstrapService.initialize();

        parkingApplicationService = new ParkingApplicationService(new PaymentProcessorAdapter());
    }

    @AfterEach
    void tearDown() throws IOException {
        Files.deleteIfExists(DB_PATH);
    }

    @Test
    void buildCheckoutSummary_normalizesVehicleAndSpots() {
        ParkingApplicationService.CheckoutSummary summary = parkingApplicationService
            .buildCheckoutSummary(
                Arrays.asList("f1-c1", " F1-c2 "),
                Arrays.asList(" ka01ab1234 ", " dl8caf7788 "),
                "COMPACT",
                2);

        assertEquals("F1-C1,F1-C2", summary.getSelectedSpots());
        assertEquals("COMPACT", summary.getVehicleType());
        assertEquals("KA01AB1234", summary.getSelections().get(0).getVehicleId());
        assertEquals("DL8CAF7788", summary.getSelections().get(1).getVehicleId());
        assertEquals(2, summary.getSpotCount());
        assertEquals(200.0, summary.getFee());
    }

    @Test
    void finalizePayment_persistsVehicleMappingAndOccupiedStatus() {
        ParkingSpot spot = firstAvailableSpot();
        String spotId = spot.getId();
        String userEmail = "driver1@example.com";
        String vehicleId = "ka01ab1234";

        PaymentRequest paymentRequest = new PaymentRequest("CASH", null, null, null, null, null, null, null);
        boolean success = parkingApplicationService.finalizePayment(
            Collections.singletonList(spotId),
            Collections.singletonList(vehicleId),
            spot.getType().name(),
            3,
            paymentRequest,
            userEmail);

        assertTrue(success);

        List<Map<String, String>> tickets = parkingApplicationService.getTicketsForUser(userEmail);
        assertTrue(!tickets.isEmpty());

        Map<String, String> ticket = tickets.get(0);
        assertEquals(spotId, ticket.get("spotId"));
        assertEquals("KA01AB1234", ticket.get("vehicleId"));
        assertEquals(DatabaseHelper.TICKET_STATUS_OCCUPIED, ticket.get("status"));
        assertEquals("CASH", ticket.get("paymentMethod"));
    }

    @Test
    void findActiveSessionByVehicle_returnsSessionForVehicle() {
        ParkingSpot spot = firstAvailableSpot();
        String spotId = spot.getId();
        String vehicleId = "mh12xy6789";

        PaymentRequest paymentRequest = new PaymentRequest("CASH", null, null, null, null, null, null, null);
        boolean success = parkingApplicationService.finalizePayment(
            Collections.singletonList(spotId),
            Collections.singletonList(vehicleId),
            spot.getType().name(),
            2,
            paymentRequest,
            "driver2@example.com");

        assertTrue(success);

        Map<String, String> activeSession = parkingApplicationService.findActiveSessionByVehicle(vehicleId);
        assertNotNull(activeSession);
        assertEquals(spotId, activeSession.get("spotId"));
        assertEquals("MH12XY6789", activeSession.get("vehicleId"));
        assertEquals(DatabaseHelper.TICKET_STATUS_OCCUPIED, activeSession.get("status"));
    }

    @Test
    void finalizePayment_mapsEachSpotToItsOwnVehicle() {
        List<String> spotIds = firstAvailableSpotIdsByType(SpotType.COMPACT, 3);
        List<String> vehicleIds = Arrays.asList("HR26B2134", "DL8CAF7788", "UP16DD9900");

        PaymentRequest paymentRequest = new PaymentRequest("CASH", null, null, null, null, null, null, null);
        boolean success = parkingApplicationService.finalizePayment(
                spotIds,
                vehicleIds,
                SpotType.COMPACT.name(),
                2,
                paymentRequest,
                "driver3@example.com");

        assertTrue(success);

        List<Map<String, String>> tickets = parkingApplicationService.getTicketsForUser("driver3@example.com");
        Map<String, String> vehicleBySpot = new HashMap<String, String>();
        for (Map<String, String> ticket : tickets) {
            vehicleBySpot.put(ticket.get("spotId"), ticket.get("vehicleId"));
        }

        for (int i = 0; i < spotIds.size(); i++) {
            assertEquals(vehicleIds.get(i), vehicleBySpot.get(spotIds.get(i)));
        }
    }

    @Test
    void concurrentBookSpot_allowsSingleWinner() throws InterruptedException, ExecutionException, TimeoutException {
        final String spotId = firstAvailableSpotId();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        Callable<Boolean> firstAttempt = new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                ready.countDown();
                start.await(5, TimeUnit.SECONDS);
                return parkingApplicationService.bookSpot(spotId, "driverA@example.com", "KA01AA1001");
            }
        };

        Callable<Boolean> secondAttempt = new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                ready.countDown();
                start.await(5, TimeUnit.SECONDS);
                return parkingApplicationService.bookSpot(spotId, "driverB@example.com", "KA01AA1002");
            }
        };

        Future<Boolean> firstResult = executor.submit(firstAttempt);
        Future<Boolean> secondResult = executor.submit(secondAttempt);

        ready.await(5, TimeUnit.SECONDS);
        start.countDown();

        boolean firstBooked = firstResult.get(10, TimeUnit.SECONDS);
        boolean secondBooked = secondResult.get(10, TimeUnit.SECONDS);

        executor.shutdownNow();

        assertNotEquals(firstBooked, secondBooked);

        List<Map<String, String>> activeSessions = parkingApplicationService.getActiveParkingSessions(50);
        long sameSpotSessions = activeSessions.stream()
                .filter(session -> spotId.equals(session.get("spotId")))
                .count();
        assertEquals(1L, sameSpotSessions);
    }

    private String firstAvailableSpotId() {
        return firstAvailableSpot().getId();
    }

    private ParkingSpot firstAvailableSpot() {
        for (ParkingFloor floor : parkingApplicationService.getFloors()) {
            for (ParkingSpot spot : floor.getSpots()) {
                if (spot.isFree()) {
                    return spot;
                }
            }
        }
        throw new IllegalStateException("No available spots found for test setup.");
    }

    private List<String> firstAvailableSpotIdsByType(SpotType type, int count) {
        List<String> ids = new java.util.ArrayList<String>();
        for (ParkingFloor floor : parkingApplicationService.getFloors()) {
            for (ParkingSpot spot : floor.getSpots()) {
                if (spot.isFree() && spot.getType() == type) {
                    ids.add(spot.getId());
                    if (ids.size() == count) {
                        return ids;
                    }
                }
            }
        }
        throw new IllegalStateException("Not enough available spots of type " + type.name());
    }
}
