package parkinglot.core;

import parkinglot.interfaces.Observer;

public class DisplayBoard implements Observer {
    private int freeSpots;

    @Override
    public void updateCount(int count) {
        this.freeSpots = count;
        showFreeSpots();
    }

    public void showFreeSpots() {
        System.out.println("Display Board: Currently " + freeSpots + " free spots available.");
    }
}