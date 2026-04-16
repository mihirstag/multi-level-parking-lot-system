package parkinglot.core;

import parkinglot.spots.ParkingSpot;
import java.util.ArrayList;
import java.util.List;

public class ParkingFloor {
    private int floorNumber; // [cite: 74]
    private List<ParkingSpot> spots; // [cite: 74]
    private DisplayBoard display; // [cite: 74]

    public ParkingFloor(int floorNumber) {
        this.floorNumber = floorNumber;
        this.spots = new ArrayList<>();
        this.display = new DisplayBoard();
    }

    public void addSpot(ParkingSpot spot) {
        spots.add(spot);
        updateDisplay(); // [cite: 74]
    }

    public void updateDisplay() { // [cite: 74]
        int freeCount = (int) spots.stream().filter(ParkingSpot::isFree).count();
        display.updateCount(freeCount); // Notifies the observer [cite: 81, 82]
    }
}