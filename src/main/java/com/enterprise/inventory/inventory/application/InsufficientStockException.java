// ─────────────────────────────────────────────────────────────
// FILE: InsufficientStockException.java
// Specific exception for the picking service — maps to 422 in the error handler
// ─────────────────────────────────────────────────────────────
package com.enterprise.inventory.inventory.application;

public class InsufficientStockException extends RuntimeException {
    public InsufficientStockException(String message) {
        super(message);
    }
}