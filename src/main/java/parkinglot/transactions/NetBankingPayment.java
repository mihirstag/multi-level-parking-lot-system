package parkinglot.transactions;

import parkinglot.interfaces.PaymentStrategy;

public class NetBankingPayment implements PaymentStrategy {
    private final String bank;
    private final String bankCode;

    public NetBankingPayment(String bank, String bankCode) {
        this.bank = bank;
        this.bankCode = bankCode;
    }

    @Override
    public boolean payAmount(double amount) {
        System.out.println("Processing net banking payment of INR " + amount + " via " + bank + ".");
        System.out.println("Bank verification code accepted: " + bankCode + ".");
        System.out.println("Payment Successful!");
        return true;
    }
}
