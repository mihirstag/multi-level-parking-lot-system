package parkinglot.users;

public class ParkingAttendant extends Account {
    public ParkingAttendant(String id, String name, String email, String phoneNumber, String password) { 
        super(id, name, email, phoneNumber, password); 
    }

    public boolean processCashPayment() {
        System.out.println("ParkingAttendant: cash payment processed at the kiosk.");
        return true;
    }

    public void handleLostTicket() {
        System.out.println("ParkingAttendant: lost ticket workflow initiated.");
    }
}