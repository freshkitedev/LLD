package com.carrental.service;

import com.carrental.enums.CarStatus;
import com.carrental.enums.PaymentStatus;
import com.carrental.enums.ReservationStatus;
import com.carrental.exception.NotFoundException;
import com.carrental.exception.ValidationException;
import com.carrental.model.Car;
import com.carrental.model.Payment;
import com.carrental.model.RentalPeriod;
import com.carrental.model.Reservation;
import com.carrental.repository.InMemoryDatabase;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Coordinates reservation lifecycle operations under the lock for the reserved car. */
public final class ReservationService {
    private final InMemoryDatabase database;
    private final CarInventoryService inventoryService;
    private final CustomerService customerService;
    private final CarSearchService searchService;
    private final PricingService pricingService;
    private final PaymentService paymentService;

    public ReservationService(InMemoryDatabase database, CarInventoryService inventoryService,
                              CustomerService customerService, CarSearchService searchService,
                              PricingService pricingService, PaymentService paymentService) {
        this.database = database;
        this.inventoryService = inventoryService;
        this.customerService = customerService;
        this.searchService = searchService;
        this.pricingService = pricingService;
        this.paymentService = paymentService;
    }

    /** Books a car; a failed simulated charge leaves no reservation record. */
    public Reservation createReservation(String customerId, String carId, RentalPeriod period,
                                         com.carrental.enums.PaymentMethod method) {
        period.validateNotInPast();
        customerService.validateEligibility(customerId, period);
        var lock = database.lockForCar(carId);
        lock.lock();
        try {
            Car car = inventoryService.getCar(carId);
            ensureActive(car);
            ensureAvailable(carId, period, null);
            BigDecimal total = pricingService.calculateTotal(car, period);
            String reservationId = UUID.randomUUID().toString();
            Payment payment = paymentService.processPayment(reservationId, "create", total, method);
            if (payment.getStatus() != PaymentStatus.SUCCESS) {
                throw new ValidationException("Payment failed; reservation was not confirmed.");
            }
            Reservation reservation = new Reservation(reservationId, customerId, carId, period, total,
                    ReservationStatus.CONFIRMED, Instant.now());
            database.reservations().put(reservationId, reservation);
            return reservation;
        } finally {
            lock.unlock();
        }
    }

    /** Modifies dates and price as one per-car operation, excluding its own old interval. */
    public Reservation modifyReservation(String reservationId, RentalPeriod newPeriod) {
        newPeriod.validateNotInPast();
        Reservation initial = getReservation(reservationId);
        var lock = database.lockForCar(initial.getCarId());
        lock.lock();
        try {
            Reservation current = getReservation(reservationId);
            ensureModifiable(current);
            if (current.getPeriod().equals(newPeriod)) {
                return current;
            }
            customerService.validateEligibility(current.getCustomerId(), newPeriod);
            Car car = inventoryService.getCar(current.getCarId());
            ensureActive(car);
            ensureAvailable(car.getId(), newPeriod, reservationId);

            BigDecimal newTotal = pricingService.calculateTotal(car, newPeriod);
            Payment previousPayment = paymentService.getActiveSuccessfulPayment(reservationId);
            if (previousPayment == null) {
                throw new ValidationException("Reservation has no active successful payment to modify.");
            }
                String operationKey = "modify:" + previousPayment.getId() + ":" + newPeriod.pickupDate()
                    + ":" + newPeriod.returnDate() + ":" + newTotal.toPlainString();
            Payment replacementPayment = paymentService.processPayment(reservationId, operationKey,
                    newTotal, previousPayment.getMethod());
            if (replacementPayment.getStatus() != PaymentStatus.SUCCESS) {
                throw new ValidationException("Payment failed; reservation was not modified.");
            }
            if (!paymentService.refundPayment(previousPayment, "replace:" + replacementPayment.getId())) {
                paymentService.refundPayment(replacementPayment, "rollback:" + replacementPayment.getId());
                throw new ValidationException("Previous payment could not be refunded; reservation was not modified.");
            }
            Reservation updated = current.withPeriodAndPrice(newPeriod, newTotal);
            database.reservations().put(reservationId, updated);
            return updated;
        } finally {
            lock.unlock();
        }
    }

    /** Cancels a confirmed reservation and refunds its active successful charge. */
    public Reservation cancelReservation(String reservationId) {
        Reservation initial = getReservation(reservationId);
        var lock = database.lockForCar(initial.getCarId());
        lock.lock();
        try {
            Reservation current = getReservation(reservationId);
            if (current.getStatus() == ReservationStatus.CANCELLED) {
                throw new ValidationException("Reservation is already cancelled.");
            }
            if (current.getStatus() == ReservationStatus.COMPLETED) {
                throw new ValidationException("Completed reservations cannot be cancelled.");
            }
            Payment payment = paymentService.getActiveSuccessfulPayment(reservationId);
            if (payment != null && !paymentService.refundPayment(payment, "cancel:" + reservationId)) {
                throw new ValidationException("Refund failed; reservation remains confirmed.");
            }
            Reservation cancelled = current.withStatus(ReservationStatus.CANCELLED);
            database.reservations().put(reservationId, cancelled);
            return cancelled;
        } finally {
            lock.unlock();
        }
    }

    public Reservation getReservation(String reservationId) {
        Reservation reservation = database.reservations().get(reservationId);
        if (reservation == null) {
            throw new NotFoundException("Reservation not found: " + reservationId);
        }
        return reservation;
    }

    public List<Reservation> getReservationsForCustomer(String customerId) {
        customerService.getCustomer(customerId);
        return database.reservations().values().stream()
                .filter(reservation -> reservation.getCustomerId().equals(customerId))
                .sorted(Comparator.comparing(Reservation::getCreatedAt))
                .toList();
    }

    private void ensureAvailable(String carId, RentalPeriod period, String excludedReservationId) {
        if (!searchService.isAvailable(carId, period, excludedReservationId)) {
            throw new ValidationException("Car is already reserved for part of the requested period.");
        }
    }

    private void ensureActive(Car car) {
        if (car.getStatus() != CarStatus.ACTIVE) {
            throw new ValidationException("Car is not active and cannot be rented: " + car.getStatus());
        }
    }

    private void ensureModifiable(Reservation reservation) {
        if (reservation.getStatus() == ReservationStatus.CANCELLED) {
            throw new ValidationException("Cancelled reservations cannot be modified.");
        }
        if (reservation.getStatus() == ReservationStatus.COMPLETED) {
            throw new ValidationException("Completed reservations cannot be modified.");
        }
    }
}