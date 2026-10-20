package com.carrental.model;

import com.carrental.enums.PaymentMethod;
import com.carrental.enums.PaymentStatus;
import java.math.BigDecimal;
import java.time.Instant;

/** Immutable simulated payment or refund record. */
public final class Payment {
    private final String id;
    private final String reservationId;
    private final BigDecimal amount;
    private final PaymentMethod method;
    private final PaymentStatus status;
    private final Instant transactionTime;

    public Payment(String id, String reservationId, BigDecimal amount, PaymentMethod method,
                   PaymentStatus status, Instant transactionTime) {
        this.id = id;
        this.reservationId = reservationId;
        this.amount = amount;
        this.method = method;
        this.status = status;
        this.transactionTime = transactionTime;
    }

    public Payment withStatus(PaymentStatus newStatus) {
        return new Payment(id, reservationId, amount, method, newStatus, transactionTime);
    }

    public String getId() { return id; }
    public String getReservationId() { return reservationId; }
    public BigDecimal getAmount() { return amount; }
    public PaymentMethod getMethod() { return method; }
    public PaymentStatus getStatus() { return status; }
    public Instant getTransactionTime() { return transactionTime; }

    @Override
    public String toString() {
        return "Payment %s: reservation=%s amount=%s method=%s status=%s at=%s".formatted(
                id, reservationId, amount.toPlainString(), method, status, transactionTime);
    }
}