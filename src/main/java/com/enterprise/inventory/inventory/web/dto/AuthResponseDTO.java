package com.enterprise.inventory.inventory.web.dto;

/**
 * Data transfer object representing the response containing authentication tokens.
 */
public record AuthResponseDTO(
        String accessToken,
        String refreshToken,
        long expiresInSeconds
) {}