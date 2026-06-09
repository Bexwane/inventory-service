// ─────────────────────────────────────────────────────────────
// FILE: AuthRequest.java — unchanged, kept for completeness
// ─────────────────────────────────────────────────────────────
package com.enterprise.inventory.inventory.web.dto;

import jakarta.validation.constraints.NotBlank;

public record AuthRequest(
        @NotBlank(message = "Username is required") String username,
        @NotBlank(message = "Password is required") String password
) {}


