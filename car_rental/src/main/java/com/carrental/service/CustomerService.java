package com.carrental.service;

import com.carrental.exception.NotFoundException;
import com.carrental.exception.ValidationException;
import com.carrental.model.Customer;
import com.carrental.model.RentalPeriod;
import com.carrental.repository.InMemoryDatabase;

/** Registers customers and checks their license eligibility for rental dates. */
public final class CustomerService {
    private final InMemoryDatabase database;

    public CustomerService(InMemoryDatabase database) {
        this.database = database;
    }

    public synchronized void registerCustomer(Customer customer) {
        if (database.customers().containsKey(customer.getId())) {
            throw new ValidationException("Customer ID already exists: " + customer.getId());
        }
        boolean licenseTaken = database.customers().values().stream()
                .anyMatch(existing -> existing.getLicenseNumber().equalsIgnoreCase(customer.getLicenseNumber()));
        if (licenseTaken) {
            throw new ValidationException("Driver's license number is already registered.");
        }
        database.customers().put(customer.getId(), customer);
    }

    public Customer getCustomer(String customerId) {
        Customer customer = database.customers().get(customerId);
        if (customer == null) {
            throw new NotFoundException("Customer not found: " + customerId);
        }
        return customer;
    }

    public void validateEligibility(String customerId, RentalPeriod period) {
        Customer customer = getCustomer(customerId);
        if (customer.getLicenseExpiryDate().isBefore(period.returnDate().minusDays(1))) {
            throw new ValidationException("Driver's license must remain valid through the rental period.");
        }
    }
}