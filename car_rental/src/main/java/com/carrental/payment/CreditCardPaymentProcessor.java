package com.carrental.payment;

import java.math.BigDecimal;

/** Simulated card processor: charges through $10,000 succeed; larger charges fail. */
public final class CreditCardPaymentProcessor implements PaymentProcessor {
    private static final BigDecimal LIMIT = new BigDecimal("10000.00");

    @Override
    public boolean processPayment(String idempotencyKey, BigDecimal amount) {
        return amount.signum() > 0 && amount.compareTo(LIMIT) <= 0;
    }

    @Override
    public boolean refundPayment(String idempotencyKey, BigDecimal amount) {
        return amount.signum() > 0;
    }
}