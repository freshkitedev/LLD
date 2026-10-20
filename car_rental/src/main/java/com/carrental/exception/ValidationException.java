package com.carrental.exception;

/** Thrown when an operation violates a business rule or required input. */
public class ValidationException extends RuntimeException {
    public ValidationException(String message) {
        super(message);
    }
}