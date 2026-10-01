package com.enterprise.inventory.exception;

/**
 * Exception thrown when there is insufficient stock to complete a reservation or pick.
 */
public class InsufficientStockException extends RuntimeException {
    public InsufficientStockException(String message) {
        super(message);
    }
}