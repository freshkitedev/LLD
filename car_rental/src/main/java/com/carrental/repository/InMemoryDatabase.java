package com.carrental.repository;

import com.carrental.model.Car;
import com.carrental.model.Customer;
import com.carrental.model.Payment;
import com.carrental.model.Reservation;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReentrantLock;

/** Shared thread-safe in-memory storage and per-car booking locks. */
public final class InMemoryDatabase {
    private final ConcurrentMap<String, Car> cars = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Customer> customers = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Reservation> reservations = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, CopyOnWriteArrayList<Payment>> payments = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, ReentrantLock> carLocks = new ConcurrentHashMap<>();

    public ConcurrentMap<String, Car> cars() { return cars; }
    public ConcurrentMap<String, Customer> customers() { return customers; }
    public ConcurrentMap<String, Reservation> reservations() { return reservations; }
    public List<Payment> paymentsFor(String reservationId) {
        return List.copyOf(payments.getOrDefault(reservationId, new CopyOnWriteArrayList<>()));
    }
    public List<Payment> paymentsForAll() {
        return payments.values().stream().flatMap(List::stream).toList();
    }
    public void addPayment(Payment payment) {
        payments.computeIfAbsent(payment.getReservationId(), ignored -> new CopyOnWriteArrayList<>()).add(payment);
    }
    public void replacePayment(Payment updated) {
        CopyOnWriteArrayList<Payment> records = payments.get(updated.getReservationId());
        if (records != null) {
            records.replaceAll(payment -> payment.getId().equals(updated.getId()) ? updated : payment);
        }
    }
    public ReentrantLock lockForCar(String carId) {
        return carLocks.computeIfAbsent(carId, ignored -> new ReentrantLock());
    }
}