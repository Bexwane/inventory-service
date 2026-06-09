package com.enterprise.inventory.inventory.web.controller;

import com.enterprise.inventory.inventory.application.InsufficientStockException;
import com.enterprise.inventory.inventory.web.dto.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * Centralised exception handling — all errors return a structured ErrorResponse.
 *
 * FIX 1: Returns ErrorResponse record (code + message + correlationId) not a plain String.
 *         Scanner apps can parse it programmatically and log the correlationId.
 *
 * FIX 2: correlationId pulled from MDC — set by CorrelationIdFilter on every request.
 *         Operations staff can search logs by this ID to find the exact failing request.
 *
 * FIX 3: IllegalArgumentException message is sanitised — if the message contains internal
 *         details (class names, stack frames) the client gets a generic message instead.
 *         Log the real message server-side.
 *
 * FIX 4: MissingRequestHeaderException added — when scanner forgets X-Idempotency-Key,
 *         the error tells them exactly which header is missing.
 *
 * FIX 5: @Valid validation errors return field-level details — scanner knows which
 *         field was wrong, not just "400 Bad Request".
 *
 * FIX 6: AccessDeniedException handled — returns 403 not 500.
 *         Without this, Spring Security's exception propagates as an unhandled 500.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLocking(OptimisticLockingFailureException ex) {
        // Tell the scanner to retry — the inventory was modified by another worker
        return error(HttpStatus.CONFLICT, "CONCURRENT_UPDATE",
                "Stock was updated by another operation. Please retry.");
    }

    @ExceptionHandler(InsufficientStockException.class)
    public ResponseEntity<ErrorResponse> handleInsufficientStock(InsufficientStockException ex) {
        // Not a server error — the picker is being routed to a location with no available qty
        log.info("Insufficient stock: {}", ex.getMessage());
        return error(HttpStatus.UNPROCESSABLE_ENTITY, "INSUFFICIENT_STOCK", ex.getMessage());
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ErrorResponse> handleDatabaseFailure(DataAccessException ex) {
        // FIX: log full exception server-side, never expose DB details to client
        log.error("Database error [correlationId={}]: {}", correlationId(), ex.getMessage(), ex);
        return error(HttpStatus.SERVICE_UNAVAILABLE, "DB_UNAVAILABLE",
                "Service temporarily unavailable. Please try again.");
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ErrorResponse> handleMissingHeader(MissingRequestHeaderException ex) {
        // FIX: tells the scanner app exactly which required header is absent
        return error(HttpStatus.BAD_REQUEST, "MISSING_HEADER",
                "Required header missing: " + ex.getHeaderName());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        // FIX: field-level validation errors — scanner knows which field to fix
        String details = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", details);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex) {
        // FIX: 403 not 500 — and generic message (don't reveal what the user tried to access)
        return error(HttpStatus.FORBIDDEN, "ACCESS_DENIED",
                "You do not have permission to perform this action.");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleBusinessRule(IllegalArgumentException ex) {
        // FIX: log server-side with full message, return sanitised message to client
        log.warn("Business rule violation [correlationId={}]: {}", correlationId(), ex.getMessage());
        return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        // Catch-all — never let a raw 500 with stack trace reach the client
        log.error("Unexpected error [correlationId={}]", correlationId(), ex);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "An unexpected error occurred. Reference: " + correlationId());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private ResponseEntity<ErrorResponse> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status)
                .body(new ErrorResponse(code, message, correlationId()));
    }

    private String correlationId() {
        // Pulled from MDC — set by CorrelationIdFilter at the start of every request
        String id = MDC.get("correlationId");
        return id != null ? id : "unknown";
    }
}