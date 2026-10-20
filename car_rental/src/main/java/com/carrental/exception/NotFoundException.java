package com.carrental.exception;

/** Thrown when a requested in-memory entity does not exist. */
public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) {
        super(message);
    }
}