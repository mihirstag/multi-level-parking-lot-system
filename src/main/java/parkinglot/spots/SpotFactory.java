package parkinglot.spots;

import parkinglot.enums.SpotType;

public class SpotFactory {
    public static ParkingSpot createSpot(String id, SpotType type) {
        // Factory logic centralizes object creation
        return new ParkingSpot(id, type);
    }
}