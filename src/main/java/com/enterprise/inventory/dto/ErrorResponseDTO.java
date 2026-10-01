package com.enterprise.inventory.dto;

/**
 * Data transfer object representing the response body when an application error occurs.
 */
public record ErrorResponseDTO(
        String code,
        String message,
        String correlationId
) {}


