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
        long now = System.currentTimeMillis();
        if ((this.status == SpotStatus.RESERVED || this.status == SpotStatus.OCCUPIED)
                && this.expiryTimeMillis > 0
                && now > this.expiryTimeMillis) {
            // Move to EXPIRED first so transition can be observed/debugged.
            this.status = SpotStatus.EXPIRED;
            this.expiryTimeMillis = 0L;
            return true;
        }

        if (this.status == SpotStatus.EXPIRED) {
            this.status = SpotStatus.AVAILABLE;
        }
        return this.status == SpotStatus.AVAILABLE;
    }

    public void reserve(int hours) {
        this.status = SpotStatus.RESERVED;
        this.expiryTimeMillis = System.currentTimeMillis() + (hours * 60 * 60 * 1000L);
    }

    public void occupy(int hours) {
        this.status = SpotStatus.OCCUPIED;
        this.expiryTimeMillis = System.currentTimeMillis() + (hours * 60 * 60 * 1000L);
    }

    public void expire() {
        this.status = SpotStatus.EXPIRED;
        this.expiryTimeMillis = 0L;
    }

    public void book(int hours) {
        // Backward compatible alias used by older flows.
        reserve(hours);
    }

    public void release() {
        this.status = SpotStatus.AVAILABLE;
        this.expiryTimeMillis = 0L;
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