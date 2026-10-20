package com.carrental.service;

import com.carrental.enums.PaymentMethod;
import com.carrental.enums.PaymentStatus;
import com.carrental.exception.NotFoundException;
import com.carrental.exception.ValidationException;
import com.carrental.model.Payment;
import com.carrental.payment.PaymentProcessor;
import com.carrental.repository.InMemoryDatabase;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Coordinates payment strategies and stores every charge outcome in memory. */
public final class PaymentService {
    private final InMemoryDatabase database;
    private final Map<PaymentMethod, PaymentProcessor> processors;
    private final ConcurrentMap<String, Payment> operations = new ConcurrentHashMap<>();

    public PaymentService(InMemoryDatabase database, Map<PaymentMethod, PaymentProcessor> processors) {
        this.database = database;
        this.processors = new EnumMap<>(processors);
    }

    public Payment processPayment(String reservationId, String operationKey, BigDecimal amount, PaymentMethod method) {
        PaymentProcessor processor = processorFor(method);
        String key = reservationId + ":charge:" + operationKey;
        return operations.computeIfAbsent(key, ignored -> {
            boolean succeeded = processor.processPayment(key, amount);
            Payment payment = new Payment(UUID.randomUUID().toString(), reservationId, amount, method,
                    succeeded ? PaymentStatus.SUCCESS : PaymentStatus.FAILED, Instant.now());
            database.addPayment(payment);
            return payment;
        });
    }

    public synchronized boolean refundPayment(Payment payment, String operationKey) {
        if (payment == null) {
            throw new ValidationException("Only a successful payment can be refunded.");
        }
        String key = payment.getReservationId() + ":refund:" + operationKey;
        Payment priorResult = operations.get(key);
        if (priorResult != null) {
            return priorResult.getStatus() == PaymentStatus.REFUNDED;
        }
        Payment currentPayment = database.paymentsFor(payment.getReservationId()).stream()
                .filter(record -> record.getId().equals(payment.getId())).findFirst().orElse(null);
        if (currentPayment == null || currentPayment.getStatus() != PaymentStatus.SUCCESS) {
            throw new ValidationException("Only a successful payment can be refunded.");
        }
        PaymentProcessor processor = processorFor(payment.getMethod());
        Payment result = operations.computeIfAbsent(key, ignored -> {
            boolean succeeded = processor.refundPayment(key, payment.getAmount());
            Payment updated = succeeded ? payment.withStatus(PaymentStatus.REFUNDED) : payment;
            database.replacePayment(updated);
            return updated;
        });
        return result.getStatus() == PaymentStatus.REFUNDED;
    }

    public Payment getPayment(String paymentId) {
        return database.paymentsForAll().stream()
                .filter(payment -> payment.getId().equals(paymentId))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Payment not found: " + paymentId));
    }

    public List<Payment> getPaymentsForReservation(String reservationId) {
        return database.paymentsFor(reservationId);
    }

    public Payment getActiveSuccessfulPayment(String reservationId) {
        return database.paymentsFor(reservationId).stream()
                .filter(payment -> payment.getStatus() == PaymentStatus.SUCCESS)
                .max((first, second) -> first.getTransactionTime().compareTo(second.getTransactionTime()))
                .orElse(null);
    }

    private PaymentProcessor processorFor(PaymentMethod method) {
        PaymentProcessor processor = processors.get(method);
        if (processor == null) {
            throw new ValidationException("No processor configured for payment method: " + method);
        }
        return processor;
    }
}