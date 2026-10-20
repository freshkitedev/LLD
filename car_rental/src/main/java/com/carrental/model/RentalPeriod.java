package com.carrental.model;

import com.carrental.exception.ValidationException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** Half-open rental interval: pickup is included and return is excluded. */
public record RentalPeriod(LocalDate pickupDate, LocalDate returnDate) {
    public RentalPeriod {
        if (pickupDate == null || returnDate == null) {
            throw new ValidationException("Pickup and return dates are required.");
        }
        if (!pickupDate.isBefore(returnDate)) {
            throw new ValidationException("Pickup date must be before return date.");
        }
    }

    public void validateNotInPast() {
        if (pickupDate.isBefore(LocalDate.now())) {
            throw new ValidationException("Pickup date cannot be in the past.");
        }
    }

    public long rentalDays() {
        return ChronoUnit.DAYS.between(pickupDate, returnDate);
    }

    public boolean overlaps(RentalPeriod other) {
        return pickupDate.isBefore(other.returnDate) && other.pickupDate.isBefore(returnDate);
    }
}