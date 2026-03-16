package parkinglot.spots;

import parkinglot.enums.SpotType;
import parkinglot.enums.SpotStatus;

public class ParkingSpot {
    private String id;
    private SpotType type;
    private SpotStatus status;
    private long expiryTimeMillis;

    public ParkingSpot(String id, SpotType type) {
        this.id = id;
        this.type = type;
        this.status = SpotStatus.AVAILABLE;
        this.expiryTimeMillis = 0;
    }

    public boolean isFree() {
        if (this.status == SpotStatus.RESERVED && System.currentTimeMillis() > this.expiryTimeMillis) {
            // Automatically release if time has expired
            this.status = SpotStatus.AVAILABLE;
        }
        return this.status == SpotStatus.AVAILABLE;
    }

    public void book(int hours) {
        this.status = SpotStatus.RESERVED;
        // Convert hours to milliseconds and set expiry time
        this.expiryTimeMillis = System.currentTimeMillis() + (hours * 60 * 60 * 1000L);
    }

    public void release() {
        this.status = SpotStatus.AVAILABLE;
    }
    
    // Add this missing getter right here!
    public String getId() {
        return id;
    }
    public SpotType getType() {
        return type;
    }

    public SpotStatus getStatus() {
        return status;
    }
}