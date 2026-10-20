package com.carrental.model;

import com.carrental.enums.CarStatus;
import com.carrental.enums.CarType;
import com.carrental.exception.ValidationException;
import java.math.BigDecimal;

/** Immutable vehicle description. Availability is derived from reservations. */
public final class Car {
    private final String id;
    private final String make;
    private final String model;
    private final int manufacturingYear;
    private final String licensePlate;
    private final CarType type;
    private final BigDecimal dailyPrice;
    private final CarStatus status;

    public Car(String id, String make, String model, int manufacturingYear, String licensePlate,
               CarType type, BigDecimal dailyPrice, CarStatus status) {
        this.id = required(id, "Car ID");
        this.make = required(make, "Make");
        this.model = required(model, "Model");
        this.licensePlate = required(licensePlate, "License plate");
        if (manufacturingYear < 1886 || manufacturingYear > java.time.Year.now().getValue() + 1) {
            throw new ValidationException("Manufacturing year is invalid.");
        }
        if (type == null || status == null) {
            throw new ValidationException("Car type and operational status are required.");
        }
        if (dailyPrice == null || dailyPrice.signum() <= 0) {
            throw new ValidationException("Daily rental price must be greater than zero.");
        }
        this.manufacturingYear = manufacturingYear;
        this.type = type;
        this.dailyPrice = dailyPrice;
        this.status = status;
    }

    private static String required(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new ValidationException(label + " is required.");
        }
        return value.trim();
    }

    public Car withStatus(CarStatus newStatus) {
        return new Car(id, make, model, manufacturingYear, licensePlate, type, dailyPrice, newStatus);
    }

    public Car withDetails(String newMake, String newModel, int newManufacturingYear, String newLicensePlate,
                           CarType newType, BigDecimal newDailyPrice) {
        return new Car(id, newMake, newModel, newManufacturingYear, newLicensePlate, newType, newDailyPrice, status);
    }

    public String getId() { return id; }
    public String getMake() { return make; }
    public String getModel() { return model; }
    public int getManufacturingYear() { return manufacturingYear; }
    public String getLicensePlate() { return licensePlate; }
    public CarType getType() { return type; }
    public BigDecimal getDailyPrice() { return dailyPrice; }
    public CarStatus getStatus() { return status; }

    @Override
    public String toString() {
        return "%s %s %s (%d), plate=%s, type=%s, %s/day, status=%s".formatted(
            id, make, model, manufacturingYear, licensePlate, type, dailyPrice.toPlainString(), status);
    }
}