package parkinglot.users;

public class Driver extends Account {
    private String licensePlate;
    private String dlNumber;
    private String aadharNumber;

    public Driver(String id, String name, String email, String phoneNumber, String password, 
                  String licensePlate, String dlNumber, String aadharNumber) {
        super(id, name, email, phoneNumber, password);
        this.licensePlate = licensePlate;
        this.dlNumber = dlNumber;
        this.aadharNumber = aadharNumber;
    }

    public void searchSpot() { 
        // Queries real-time floor data 
    }
    
    public void bookSpot() { 
        // Triggers the system to change spot status 
    }
    
    public void payFee() { 
        // Successful payment triggers spot reset 
    }

    public String getLicensePlate() {
        return licensePlate;
    }

    public String getDlNumber() {
        return dlNumber;
    }

    public String getAadharNumber() {
        return aadharNumber;
    }
}