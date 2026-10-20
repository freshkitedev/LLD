package com.carrental.service;

import com.carrental.enums.CarStatus;
import com.carrental.exception.NotFoundException;
import com.carrental.exception.ValidationException;
import com.carrental.model.Car;
import com.carrental.repository.InMemoryDatabase;
import java.util.List;

/** Manages registered vehicles and their operational status. */
public final class CarInventoryService {
    private final InMemoryDatabase database;

    public CarInventoryService(InMemoryDatabase database) {
        this.database = database;
    }

    public synchronized void addCar(Car car) {
        if (database.cars().containsKey(car.getId())) {
            throw new ValidationException("Car ID already exists: " + car.getId());
        }
        boolean plateTaken = database.cars().values().stream()
                .anyMatch(existing -> existing.getLicensePlate().equalsIgnoreCase(car.getLicensePlate()));
        if (plateTaken) {
            throw new ValidationException("License plate already exists: " + car.getLicensePlate());
        }
        database.cars().put(car.getId(), car);
    }

    public Car getCar(String carId) {
        Car car = database.cars().get(carId);
        if (car == null) {
            throw new NotFoundException("Car not found: " + carId);
        }
        return car;
    }

    public void updateCar(String carId, String make, String model, int manufacturingYear,
                          String licensePlate, com.carrental.enums.CarType type, java.math.BigDecimal dailyPrice) {
        var lock = database.lockForCar(carId);
        lock.lock();
        try {
            Car current = getCar(carId);
            boolean plateTaken = database.cars().values().stream()
                    .anyMatch(existing -> !existing.getId().equals(carId)
                            && existing.getLicensePlate().equalsIgnoreCase(licensePlate));
            if (plateTaken) {
                throw new ValidationException("License plate already exists: " + licensePlate);
            }
            database.cars().put(carId, current.withDetails(make, model, manufacturingYear,
                    licensePlate, type, dailyPrice));
        } finally {
            lock.unlock();
        }
    }

    public List<Car> getAllCars() {
        return database.cars().values().stream().sorted((first, second) -> first.getId().compareTo(second.getId())).toList();
    }

    public void updateStatus(String carId, CarStatus status) {
        if (status == null) {
            throw new ValidationException("Operational status is required.");
        }
        var lock = database.lockForCar(carId);
        lock.lock();
        try {
            Car car = getCar(carId);
            database.cars().put(carId, car.withStatus(status));
        } finally {
            lock.unlock();
        }
    }

    public void removeCar(String carId) {
        var lock = database.lockForCar(carId);
        lock.lock();
        try {
            getCar(carId);
            boolean hasHistory = database.reservations().values().stream()
                    .anyMatch(reservation -> reservation.getCarId().equals(carId));
            if (hasHistory) {
                throw new ValidationException("Car cannot be removed because reservation records reference it.");
            }
            database.cars().remove(carId);
        } finally {
            lock.unlock();
        }
    }
}