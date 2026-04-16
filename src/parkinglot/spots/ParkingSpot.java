package parkinglot.spots;

import parkinglot.enums.SpotType;
import parkinglot.enums.SpotStatus;

public class ParkingSpot {
    private String id;
    private SpotType type;
    private SpotStatus status;

    public ParkingSpot(String id, SpotType type) {
        this.id = id;
        this.type = type;
        this.status = SpotStatus.AVAILABLE;
    }

    public boolean isFree() {
        return this.status == SpotStatus.AVAILABLE;
    }

    public void book() {
        this.status = SpotStatus.RESERVED;
    }

    public void release() {
        this.status = SpotStatus.AVAILABLE;
    }
    
    // Add this missing getter right here!
    public String getId() {
        return id;
    }
}