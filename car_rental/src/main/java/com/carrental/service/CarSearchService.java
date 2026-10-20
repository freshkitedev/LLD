package com.carrental.service;

import com.carrental.enums.CarStatus;
import com.carrental.enums.ReservationStatus;
import com.carrental.exception.ValidationException;
import com.carrental.model.Car;
import com.carrental.model.RentalPeriod;
import com.carrental.repository.InMemoryDatabase;
import java.math.BigDecimal;
import java.util.List;

/** Searches active cars and derives period availability from confirmed reservations. */
public final class CarSearchService {
    private final InMemoryDatabase database;

    public CarSearchService(InMemoryDatabase database) {
        this.database = database;
    }

    public List<Car> search(CarSearchCriteria criteria) {
        CarSearchCriteria filters = criteria == null
                ? new CarSearchCriteria(null, null, null, null, null) : criteria;
        BigDecimal minimum = filters.minimumDailyPrice();
        BigDecimal maximum = filters.maximumDailyPrice();
        if (minimum != null && minimum.signum() < 0 || maximum != null && maximum.signum() < 0) {
            throw new ValidationException("Price filters cannot be negative.");
        }
        if (minimum != null && maximum != null && minimum.compareTo(maximum) > 0) {
            throw new ValidationException("Minimum daily price cannot exceed maximum daily price.");
        }
        boolean hasPickup = filters.pickupDate() != null;
        boolean hasReturn = filters.returnDate() != null;
        if (hasPickup != hasReturn) {
            throw new ValidationException("Both pickup and return dates must be provided for availability search.");
        }
        RentalPeriod requested = hasPickup ? new RentalPeriod(filters.pickupDate(), filters.returnDate()) : null;
        if (requested != null) {
            requested.validateNotInPast();
        }

        return database.cars().values().stream()
                .filter(car -> car.getStatus() == CarStatus.ACTIVE)
                .filter(car -> filters.type() == null || car.getType() == filters.type())
                .filter(car -> minimum == null || car.getDailyPrice().compareTo(minimum) >= 0)
                .filter(car -> maximum == null || car.getDailyPrice().compareTo(maximum) <= 0)
                .filter(car -> requested == null || isAvailable(car.getId(), requested, null))
                .sorted((first, second) -> first.getId().compareTo(second.getId()))
                .toList();
    }

    public boolean isAvailable(String carId, RentalPeriod requested, String excludedReservationId) {
        return database.reservations().values().stream()
                .filter(reservation -> reservation.getCarId().equals(carId))
                .filter(reservation -> reservation.getStatus() == ReservationStatus.CONFIRMED)
                .filter(reservation -> !reservation.getId().equals(excludedReservationId))
                .noneMatch(reservation -> reservation.getPeriod().overlaps(requested));
    }
}