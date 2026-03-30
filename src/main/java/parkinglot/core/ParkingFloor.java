package parkinglot.core;

import parkinglot.spots.ParkingSpot;
import java.util.ArrayList;
import java.util.List;

public class ParkingFloor {
    private int floorNumber;
    private List<ParkingSpot> spots;
    private DisplayBoard display;

    public ParkingFloor(int floorNumber) {
        this.floorNumber = floorNumber;
        this.spots = new ArrayList<>();
        this.display = new DisplayBoard();
    }

    public void addSpot(ParkingSpot spot) {
        spots.add(spot);
        updateDisplay();
    }

    public void updateDisplay() {
        int freeCount = (int) spots.stream().filter(ParkingSpot::isFree).count();
        display.updateCount(freeCount);
    }
    public int getFloorNumber() {
        return floorNumber;
    }

    public List<ParkingSpot> getSpots() {
        return spots;
    }
}