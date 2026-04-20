package com.team12.parkinglot_web.service;

import javax.annotation.PostConstruct;

import org.springframework.stereotype.Service;

import parkinglot.core.ParkingFloor;
import parkinglot.core.ParkingLot;
import parkinglot.db.DatabaseHelper;
import parkinglot.enums.SpotType;
import parkinglot.spots.SpotFactory;

@Service
public class ParkingLotBootstrapService {

    private static final String LOT_ADDRESS = "123 Tech Park, Bengaluru";

    @PostConstruct
    public void initialize() {
        DatabaseHelper.initializeDatabase();

        DatabaseHelper.ensureUserAccount("System Administrator", "admin@parkmanager.com", "Admin@123",
            DatabaseHelper.ROLE_ADMIN);
        DatabaseHelper.ensureUserAccount("Parking Attendant", "attendant@parkmanager.com", "Attend@123",
            DatabaseHelper.ROLE_ATTENDANT);
        DatabaseHelper.ensureUserAccount("Security Guard", "guard@parkmanager.com", "Guard@123",
            DatabaseHelper.ROLE_SECURITY_GUARD);

        ParkingLot lot = ParkingLot.getInstance(LOT_ADDRESS);
        if (lot.getFloors().isEmpty()) {
            for (int i = 1; i <= 3; i++) {
                ParkingFloor floor = new ParkingFloor(i);
                for (int j = 1; j <= 20; j++) {
                    floor.addSpot(SpotFactory.createSpot("F" + i + "-C" + j, SpotType.COMPACT));
                }
                for (int j = 1; j <= 20; j++) {
                    floor.addSpot(SpotFactory.createSpot("F" + i + "-L" + j, SpotType.LARGE));
                }
                for (int j = 1; j <= 5; j++) {
                    floor.addSpot(SpotFactory.createSpot("F" + i + "-H" + j, SpotType.HANDICAPPED));
                }
                for (int j = 1; j <= 5; j++) {
                    floor.addSpot(SpotFactory.createSpot("F" + i + "-E" + j, SpotType.EV_CHARGING));
                }
                lot.addFloor(floor);
            }
        }

        // Restore active spot reservations from persistent tickets.
        DatabaseHelper.syncActiveSpots(lot);
    }
}
