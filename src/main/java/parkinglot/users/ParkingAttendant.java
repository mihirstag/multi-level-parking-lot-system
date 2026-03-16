package parkinglot.users;

public class ParkingAttendant extends Account {
    public ParkingAttendant(String id, String name, String email, String phoneNumber, String password) { 
        super(id, name, email, phoneNumber, password); 
    }

    public boolean processCashPayment() { return true; }
    public void handleLostTicket() { /* Manual override logic */ }
}