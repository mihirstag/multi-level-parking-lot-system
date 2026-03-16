package parkinglot.transactions;

import parkinglot.interfaces.PaymentStrategy;

public class Payment {
    private double amount;
    private PaymentStrategy strategy;

    public Payment(double amount) {
        this.amount = amount;
    }

    public void setStrategy(PaymentStrategy strategy) {
        this.strategy = strategy;
    }

    public boolean processPayment() {
        if (strategy == null) {
            System.out.println("Payment strategy not set!");
            return false;
        }
        return strategy.payAmount(amount);
    }
}