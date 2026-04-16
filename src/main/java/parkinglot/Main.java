package parkinglot;

import parkinglot.core.*;
import parkinglot.db.DatabaseHelper;
import parkinglot.enums.*;
import parkinglot.spots.*;
import parkinglot.transactions.*;
import parkinglot.users.Driver;

public class Main {
    public static void main(String[] args) {
        System.out.println("=== Initializing Multi-Level Parking Lot System ===");
        
        // 1. Initialize Database Connection
        DatabaseHelper.initializeDatabase();
        
        // 2. Setup Parking Lot (Singleton Pattern)
        ParkingLot lot = ParkingLot.getInstance("123 Tech Park, Bengaluru");
        
        // 3. Setup Floors and Spots (Observer & Factory Patterns)
        System.out.println("\n--- Setting up Infrastructure (150 Spots) ---");
        
        ParkingFloor firstFloorForTest = null;
        ParkingSpot firstSpotForTest = null;

        for (int i = 1; i <= 3; i++) {
            ParkingFloor floor = new ParkingFloor(i);
            
            // Generate 20 COMPACT, 20 LARGE, 5 HANDICAPPED, 5 EV per floor
            for (int j = 1; j <= 20; j++) {
                ParkingSpot spot = SpotFactory.createSpot("F" + i + "-C" + j, SpotType.COMPACT);
                floor.addSpot(spot);
                // Capture the very first spot (F1-C1) for our test booking below
                if (i == 1 && j == 1) firstSpotForTest = spot; 
            }
            for (int j = 1; j <= 20; j++) floor.addSpot(SpotFactory.createSpot("F" + i + "-L" + j, SpotType.LARGE));
            for (int j = 1; j <= 5; j++) floor.addSpot(SpotFactory.createSpot("F" + i + "-H" + j, SpotType.HANDICAPPED));
            for (int j = 1; j <= 5; j++) floor.addSpot(SpotFactory.createSpot("F" + i + "-E" + j, SpotType.EV_CHARGING));
            
            lot.addFloor(floor);
            if (i == 1) firstFloorForTest = floor;
        }
        
        // 4. Register a Driver (UC-01)
        System.out.println("\n--- Driver Registration ---");
        Driver driver = new Driver("D001", "Mihir", "mihir@example.com", "9876543210", 
                                   "password123", "KA-01-AB-1234", "DL-98765", "1234-5678-9012");
        System.out.println("Driver Mihir registered successfully.");
        
        // 5. Book a Spot (UC-04) & Generate Ticket (UC-07)
        System.out.println("\n--- Booking Process ---");
        if (firstSpotForTest != null && firstSpotForTest.isFree()) {
            firstSpotForTest.book(1);
            System.out.println("Spot " + firstSpotForTest.getId() + " Status: RESERVED");
            firstFloorForTest.updateDisplay(); // Triggers DisplayBoard observer
            
            Ticket ticket = new Ticket("T001", firstSpotForTest);
            System.out.println("Ticket T001 generated.");
            
            // 6. Process Payment (Strategy Pattern) - Redirects to secure site (UC-11)
            Payment payment = new Payment(50.0); // ₹50 fee
            payment.setStrategy(new CreditCardPayment("mihir@example.com"));
            
            if (payment.processPayment()) {
                firstSpotForTest.release(); // Auto-Release Spot System Logic
                System.out.println("Spot " + firstSpotForTest.getId() + " Status: AVAILABLE");
                firstFloorForTest.updateDisplay();
            }
        }
    }
}