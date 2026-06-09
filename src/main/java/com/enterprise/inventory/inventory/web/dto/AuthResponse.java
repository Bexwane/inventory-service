// ─────────────────────────────────────────────────────────────
// FILE: AuthResponse.java
// FIX: now returns both access token and refresh token
// ─────────────────────────────────────────────────────────────
package com.enterprise.inventory.inventory.web.dto;

public record AuthResponse(
        String accessToken,
        String refreshToken,    // FIX: workers use this to get a new access token
        long expiresInSeconds   // tells the scanner app when to refresh
) {}