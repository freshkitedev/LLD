package com.carrental.model;

import com.carrental.enums.ReservationStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Immutable reservation record with controlled lifecycle-copy methods. */
public final class Reservation {
    private final String id;
    private final String customerId;
    private final String carId;
    private final LocalDate pickupDate;
    private final LocalDate returnDate;
    private final BigDecimal totalPrice;
    private final ReservationStatus status;
    private final Instant createdAt;

    public Reservation(String id, String customerId, String carId, RentalPeriod period,
                       BigDecimal totalPrice, ReservationStatus status, Instant createdAt) {
        this.id = id;
        this.customerId = customerId;
        this.carId = carId;
        this.pickupDate = period.pickupDate();
        this.returnDate = period.returnDate();
        this.totalPrice = totalPrice;
        this.status = status;
        this.createdAt = createdAt;
    }

    public RentalPeriod getPeriod() { return new RentalPeriod(pickupDate, returnDate); }
    public Reservation withPeriodAndPrice(RentalPeriod period, BigDecimal price) {
        return new Reservation(id, customerId, carId, period, price, status, createdAt);
    }
    public Reservation withStatus(ReservationStatus newStatus) {
        return new Reservation(id, customerId, carId, getPeriod(), totalPrice, newStatus, createdAt);
    }

    public String getId() { return id; }
    public String getCustomerId() { return customerId; }
    public String getCarId() { return carId; }
    public LocalDate getPickupDate() { return pickupDate; }
    public LocalDate getReturnDate() { return returnDate; }
    public BigDecimal getTotalPrice() { return totalPrice; }
    public ReservationStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }

    @Override
    public String toString() {
        return "Reservation %s: customer=%s car=%s [%s, %s) total=%s status=%s created=%s".formatted(
                id, customerId, carId, pickupDate, returnDate, totalPrice.toPlainString(), status, createdAt);
    }
}