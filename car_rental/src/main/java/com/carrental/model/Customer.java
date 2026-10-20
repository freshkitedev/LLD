package com.carrental.model;

import com.carrental.exception.ValidationException;
import java.time.LocalDate;

/** Customer profile containing only information needed to administer rentals. */
public final class Customer {
    private final String id;
    private final String fullName;
    private final String contactNumber;
    private final String email;
    private final String licenseNumber;
    private final LocalDate licenseExpiryDate;

    public Customer(String id, String fullName, String contactNumber, String email,
                    String licenseNumber, LocalDate licenseExpiryDate) {
        this.id = required(id, "Customer ID");
        this.fullName = required(fullName, "Full name");
        this.contactNumber = required(contactNumber, "Contact number");
        this.email = required(email, "Email address");
        this.licenseNumber = required(licenseNumber, "Driver's license number");
        if (!email.contains("@") || email.startsWith("@") || email.endsWith("@")) {
            throw new ValidationException("Email address is invalid.");
        }
        if (licenseExpiryDate == null || licenseExpiryDate.isBefore(LocalDate.now())) {
            throw new ValidationException("Driver's license must be currently valid.");
        }
        this.licenseExpiryDate = licenseExpiryDate;
    }

    private static String required(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new ValidationException(label + " is required.");
        }
        return value.trim();
    }

    public String getId() { return id; }
    public String getFullName() { return fullName; }
    public String getContactNumber() { return contactNumber; }
    public String getEmail() { return email; }
    public String getLicenseNumber() { return licenseNumber; }
    public LocalDate getLicenseExpiryDate() { return licenseExpiryDate; }

    @Override
    public String toString() {
        return "%s (%s), %s, license valid through %s".formatted(id, fullName, email, licenseExpiryDate);
    }
}