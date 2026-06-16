package com.enterprise.inventory.inventory.web.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Data transfer object representing the request body for user authentication.
 */
public record AuthDTO(
        @NotBlank(message = "Username is required") String username,
        @NotBlank(message = "Password is required") String password
) {}


