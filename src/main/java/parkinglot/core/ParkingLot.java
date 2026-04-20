package parkinglot.core;

import java.util.ArrayList;
import java.util.List;

public class ParkingLot {
    private static volatile ParkingLot instance;
    private final String address;
    private final List<ParkingFloor> floors;

    private ParkingLot(String address) {
        this.address = address;
        this.floors = new ArrayList<>();
    }

    public static ParkingLot getInstance(String address) {
        if (instance == null) {
            synchronized (ParkingLot.class) {
                if (instance == null) {
                    instance = new ParkingLot(address);
                }
            }
        }
        return instance;
    }

    public void addFloor(ParkingFloor floor) {
        floors.add(floor);
    }

    public int getAvailableSpots() {
        int totalFreeSpots = 0;
        for (ParkingFloor floor : floors) {
            totalFreeSpots += (int) floor.getSpots().stream().filter(spot -> spot.isFree()).count();
        }
        return totalFreeSpots;
    }

    public String getAddress() {
        return address;
    }

    public List<ParkingFloor> getFloors() {
        return floors;
    }
}