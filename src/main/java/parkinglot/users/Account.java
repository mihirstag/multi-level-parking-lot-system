package parkinglot.users;

public abstract class Account {
    protected String id;
    protected String name;
    protected String email;
    protected String phoneNumber;
    protected String password;

    public Account(String id, String name, String email, String phoneNumber, String password) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.phoneNumber = phoneNumber;
        this.password = password;
    }

    public boolean login(String inputPassword) {
        return this.password.equals(inputPassword);
    }
    
    // Getters and setters omitted for brevity
}