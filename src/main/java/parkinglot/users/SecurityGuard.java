package parkinglot.users;

public class SecurityGuard extends Account {
    public SecurityGuard(String id, String name, String email, String phoneNumber, String password) { 
        super(id, name, email, phoneNumber, password); 
    }

    public void manualGateOverride() {
        System.out.println("SecurityGuard: manual gate override executed.");
    }
}