package parkinglot.transactions;

import parkinglot.spots.ParkingSpot;
import java.time.LocalDateTime;
import java.time.Duration;

public class Ticket {
    private String id;
    private LocalDateTime entryTime;
    private ParkingSpot spot;

    public Ticket(String id, ParkingSpot spot) {
        this.id = id;
        this.entryTime = LocalDateTime.now();
        this.spot = spot;
    }

    public double calculateDuration() { //
        // Simplified for testing: returning hours passed
        LocalDateTime exitTime = LocalDateTime.now();
        Duration duration = Duration.between(entryTime, exitTime);
        return Math.max(1, duration.toHours()); // Minimum 1 hour
    }
    
    public ParkingSpot getSpot() { return spot; }
}