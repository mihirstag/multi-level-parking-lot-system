package parkinglot.transactions;

import parkinglot.interfaces.PaymentStrategy;

public class CashPayment implements PaymentStrategy {
    @Override
    public boolean payAmount(double amount) {
        System.out.println("Processing cash payment of ₹" + amount + " at the kiosk.");
        System.out.println("Payment Successful!");
        return true;
    }
}