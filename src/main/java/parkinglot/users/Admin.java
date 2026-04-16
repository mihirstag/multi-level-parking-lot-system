package parkinglot.users;

public class Admin extends Account {
    public Admin(String id, String name, String email, String phoneNumber, String password) { 
        super(id, name, email, phoneNumber, password); 
    }

    public void updateRateCard() { /* Update pricing logic */ }
    public void viewRevenueReports() { /* Aggregate financial data */ }
}