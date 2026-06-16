package com.enterprise.inventory.inventory.web.controller;

import com.enterprise.inventory.inventory.application.InsufficientStockException;
import com.enterprise.inventory.inventory.web.dto.ErrorResponseDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler exceptionHandler;

    @BeforeEach
    void setUp() {
        exceptionHandler = new GlobalExceptionHandler();
        MDC.clear();
        MDC.put("correlationId", "test-corr-id-123");
    }

    @Test
    void handleOptimisticLocking_ReturnsConflict() {
        OptimisticLockingFailureException ex = new OptimisticLockingFailureException("Stale data");
        ResponseEntity<ErrorResponseDTO> response = exceptionHandler.handleOptimisticLocking(ex);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("CONCURRENT_UPDATE", response.getBody().code());
        assertEquals("test-corr-id-123", response.getBody().correlationId());
    }

    @Test
    void handleInsufficientStock_ReturnsUnprocessableEntity() {
        InsufficientStockException ex = new InsufficientStockException("Not enough stock");
        ResponseEntity<ErrorResponseDTO> response = exceptionHandler.handleInsufficientStock(ex);

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("INSUFFICIENT_STOCK", response.getBody().code());
        assertEquals("test-corr-id-123", response.getBody().correlationId());
    }

    @Test
    void handleIllegalArgumentException_ReturnsBadRequest() {
        IllegalArgumentException ex = new IllegalArgumentException("Invalid input parameter");
        ResponseEntity<ErrorResponseDTO> response = exceptionHandler.handleBusinessRule(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("INVALID_REQUEST", response.getBody().code());
        assertEquals("Invalid input parameter", response.getBody().message());
        assertEquals("test-corr-id-123", response.getBody().correlationId());
    }

    @Test
    void handleUnexpectedException_ReturnsInternalServerErrorWithCorrelationIdInMessage() {
        Exception ex = new NullPointerException("Something exploded");
        ResponseEntity<ErrorResponseDTO> response = exceptionHandler.handleUnexpected(ex);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("INTERNAL_ERROR", response.getBody().code());
        assertEquals("test-corr-id-123", response.getBody().correlationId());
        assertEquals("An unexpected error occurred. Reference: test-corr-id-123", response.getBody().message());
    }
}
