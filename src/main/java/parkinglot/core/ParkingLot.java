package parkinglot.core;

import java.util.ArrayList;
import java.util.List;

public class ParkingLot {
    private static ParkingLot instance; // Singleton instance [cite: 67]
    private String address; // [cite: 67]
    private List<ParkingFloor> floors; // [cite: 67]

    // Private constructor ensures single point of access [cite: 56]
    private ParkingLot(String address) {
        this.address = address;
        this.floors = new ArrayList<>();
    }

    // Standard Singleton getInstance method [cite: 65, 67]
    public static ParkingLot getInstance(String address) {
        if (instance == null) {
            instance = new ParkingLot(address);
        }
        return instance;
    }

    public void addFloor(ParkingFloor floor) {
        floors.add(floor);
    }

    public int getAvailableSpots() { // [cite: 68]
        // Logic to aggregate available spots across all floors
        return 0; // Placeholder for logic
    }
    public List<ParkingFloor> getFloors() {
        return floors;
    }
}