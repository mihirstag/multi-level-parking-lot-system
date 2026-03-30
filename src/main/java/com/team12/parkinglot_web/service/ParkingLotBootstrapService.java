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
