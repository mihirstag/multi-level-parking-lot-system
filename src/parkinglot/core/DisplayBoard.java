package parkinglot.core;

import parkinglot.interfaces.Observer;

public class DisplayBoard implements Observer {
    private int freeSpots;

    @Override
    public void updateCount(int count) {
        this.freeSpots = count; // Updates automatically [cite: 82, 110]
        showFreeSpots(); // [cite: 83]
    }

    public void showFreeSpots() {
        System.out.println("Display Board: Currently " + freeSpots + " free spots available."); // [cite: 83]
    }
}