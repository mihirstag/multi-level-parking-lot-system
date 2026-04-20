package com.team12.parkinglot_web.payment;

import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import parkinglot.transactions.CashPayment;
import parkinglot.transactions.CreditCardPayment;
import parkinglot.transactions.NetBankingPayment;
import parkinglot.transactions.Payment;

@Component
public class PaymentProcessorAdapter {

    private static final Pattern CARD_OWNER_PATTERN = Pattern.compile("^[A-Za-z][A-Za-z\\s'.-]{1,48}$");
    private static final Pattern CARD_NUMBER_PATTERN = Pattern.compile("^\\d{16}$");
    private static final Pattern EXPIRY_MONTH_PATTERN = Pattern.compile("^(0?[1-9]|1[0-2])$");
    private static final Pattern EXPIRY_YEAR_PATTERN = Pattern.compile("^\\d{2,4}$");
    private static final Pattern CVV_PATTERN = Pattern.compile("^\\d{3,4}$");
    private static final Pattern BANK_CODE_PATTERN = Pattern.compile("^\\d{4,8}$");

    public boolean processPayment(PaymentRequest paymentRequest, String userEmail, double amount) {
        String method = paymentRequest.getNormalizedPaymentMethod();
        Payment payment = new Payment(amount);

        if ("CREDIT_CARD".equals(method)) {
            validateCreditCard(paymentRequest);
            payment.setStrategy(new CreditCardPayment(userEmail));
        } else if ("NET_BANKING".equals(method)) {
            validateNetBanking(paymentRequest);
            payment.setStrategy(new NetBankingPayment(paymentRequest.getBank(), paymentRequest.getBankCode()));
        } else if ("CASH".equals(method)) {
            payment.setStrategy(new CashPayment());
        } else {
            throw new IllegalArgumentException("Unsupported payment method. Use CREDIT_CARD, NET_BANKING, or CASH.");
        }

        return payment.processPayment();
    }

    private void validateCreditCard(PaymentRequest paymentRequest) {
        require(CARD_OWNER_PATTERN.matcher(paymentRequest.getCardOwner()).matches(), "Invalid card owner name.");
        require(CARD_NUMBER_PATTERN.matcher(paymentRequest.getCardNumber()).matches(), "Card number must be 16 digits.");
        require(EXPIRY_MONTH_PATTERN.matcher(paymentRequest.getExpiryMonth()).matches(), "Invalid expiry month.");
        require(EXPIRY_YEAR_PATTERN.matcher(paymentRequest.getExpiryYear()).matches(), "Invalid expiry year.");
        require(CVV_PATTERN.matcher(paymentRequest.getCvv()).matches(), "Invalid CVV.");
    }

    private void validateNetBanking(PaymentRequest paymentRequest) {
        require(!paymentRequest.getBank().isEmpty(), "Bank selection is required.");
        require(BANK_CODE_PATTERN.matcher(paymentRequest.getBankCode()).matches(), "Invalid bank verification code.");
    }

    private void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }
}
