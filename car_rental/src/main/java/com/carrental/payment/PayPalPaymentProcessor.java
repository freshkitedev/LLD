package com.carrental.payment;

import java.math.BigDecimal;

/** Simulated PayPal processor with the same deterministic test threshold as card payments. */
public final class PayPalPaymentProcessor implements PaymentProcessor {
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