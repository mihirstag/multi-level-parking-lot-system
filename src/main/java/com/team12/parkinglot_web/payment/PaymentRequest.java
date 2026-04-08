package com.team12.parkinglot_web.payment;

import java.util.Locale;

public class PaymentRequest {
    private final String paymentMethod;
    private final String cardOwner;
    private final String cardNumber;
    private final String expiryMonth;
    private final String expiryYear;
    private final String cvv;
    private final String bank;
    private final String bankCode;

    public PaymentRequest(String paymentMethod,
                          String cardOwner,
                          String cardNumber,
                          String expiryMonth,
                          String expiryYear,
                          String cvv,
                          String bank,
                          String bankCode) {
        this.paymentMethod = safe(paymentMethod);
        this.cardOwner = safe(cardOwner);
        this.cardNumber = safe(cardNumber);
        this.expiryMonth = safe(expiryMonth);
        this.expiryYear = safe(expiryYear);
        this.cvv = safe(cvv);
        this.bank = safe(bank);
        this.bankCode = safe(bankCode);
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public String getNormalizedPaymentMethod() {
        return paymentMethod.toUpperCase(Locale.ROOT);
    }

    public String getCardOwner() {
        return cardOwner;
    }

    public String getCardNumber() {
        return cardNumber;
    }

    public String getExpiryMonth() {
        return expiryMonth;
    }

    public String getExpiryYear() {
        return expiryYear;
    }

    public String getCvv() {
        return cvv;
    }

    public String getBank() {
        return bank;
    }

    public String getBankCode() {
        return bankCode;
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
