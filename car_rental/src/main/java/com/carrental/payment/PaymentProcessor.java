package com.carrental.payment;

import java.math.BigDecimal;

/** Strategy contract for simulated, idempotent charge and refund operations. */
public interface PaymentProcessor {
    boolean processPayment(String idempotencyKey, BigDecimal amount);
    boolean refundPayment(String idempotencyKey, BigDecimal amount);
}