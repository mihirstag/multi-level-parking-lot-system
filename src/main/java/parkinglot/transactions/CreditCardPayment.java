package parkinglot.transactions;

import parkinglot.interfaces.PaymentStrategy;
import java.util.UUID;

public class CreditCardPayment implements PaymentStrategy {
    private String email;

    public CreditCardPayment(String email) {
        this.email = email;
    }

    @Override
    public boolean payAmount(double amount) {
        // Simulating the creation of a secure checkout session
        String transactionId = UUID.randomUUID().toString();
        String secureCheckoutUrl = "https://secure.paymentgateway.com/checkout/" + transactionId;
        
        System.out.println("\n--- EXTERNAL PAYMENT REDIRECT ---");
        System.out.println("Amount Due: ₹" + amount);
        System.out.println("Redirecting " + email + " to secure site...");
        System.out.println("Please complete your payment at: " + secureCheckoutUrl);
        System.out.println("Waiting for gateway authorization...");
        
        // In a real app, you would wait for a webhook callback from the gateway.
        // Here, we simulate a successful return from the secure site.
        System.out.println("Authorization received. Payment Successful!\n");
        return true;
    }
}