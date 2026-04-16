package parkinglot.interfaces;

public interface PaymentStrategy {
    boolean payAmount(double amount);
}