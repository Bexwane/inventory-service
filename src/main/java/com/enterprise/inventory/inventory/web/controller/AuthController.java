package com.enterprise.inventory.inventory.web.controller;

import com.enterprise.inventory.inventory.infrastructure.config.JwtUtil;
import com.enterprise.inventory.inventory.web.dto.AuthRequest;
import com.enterprise.inventory.inventory.web.dto.AuthResponse;
import com.enterprise.inventory.inventory.infrastructure.config.TokenBlacklistService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.web.bind.annotation.*;

/**
 * Handles authentication and token refresh.
 *
 * FIX 1: Returns both access token and refresh token on login.
 *         Scanner app stores refresh token and uses it to renew access token
 *         mid-shift without the worker having to re-enter credentials.
 *
 * FIX 2: /refresh endpoint added.
 *         Takes a valid refresh token, returns a new access token.
 *         The old refresh token is left valid (single-use refresh tokens
 *         are optional and add complexity — implement if security requires it).
 *
 * FIX 3: /logout endpoint added.
 *         Adds the token to the Redis blacklist so it cannot be used again
 *         even before it expires. Critical for lost scanners and terminated workers.
 *
 * FIX 4: Generic error message on failed login.
 *         Original code would bubble up Spring exceptions that revealed
 *         whether the username or password was the problem.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final UserDetailsService    userDetailsService;
    private final JwtUtil               jwtUtil;
    private final TokenBlacklistService tokenBlacklistService;

    /**
     * Login — exchange credentials for access + refresh tokens.
     *
     * POST /api/v1/auth/login
     * Body: { "username": "...", "password": "..." }
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody AuthRequest request) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.username(), request.password()));
        } catch (AuthenticationException ex) {
            // FIX: same error for wrong username OR wrong password
            // Never reveal which one failed — prevents username enumeration
            log.warn("Failed login attempt for username: {}", request.username());
            return ResponseEntity.status(401)
                    .body(null); // client gets 401 with empty body — no detail
        }

        UserDetails userDetails = userDetailsService.loadUserByUsername(request.username());

        String accessToken  = jwtUtil.generateAccessToken(userDetails);
        String refreshToken = jwtUtil.generateRefreshToken(userDetails);

        log.info("Login successful for user: {}", request.username());

        return ResponseEntity.ok(new AuthResponse(
                accessToken,
                refreshToken,
                3600L   // access token valid for 1 hour — matches jwt.expiration-ms
        ));
    }

    /**
     * Refresh — exchange a valid refresh token for a new access token.
     * Scanner app calls this before the access token expires to stay logged in mid-shift.
     *
     * POST /api/v1/auth/refresh
     * Header: Authorization: Bearer <refresh_token>
     */
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(
            @RequestHeader("Authorization") String authHeader) {

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.badRequest().build();
        }

        String refreshToken = authHeader.substring(7);

        if (!jwtUtil.isRefreshTokenValid(refreshToken)) {
            return ResponseEntity.status(401).build();
        }

        String username = jwtUtil.extractUsername(refreshToken);
        UserDetails userDetails = userDetailsService.loadUserByUsername(username);

        String newAccessToken = jwtUtil.generateAccessToken(userDetails);

        return ResponseEntity.ok(new AuthResponse(
                newAccessToken,
                refreshToken,  // same refresh token — still valid until its own expiry
                3600L
        ));
    }

    /**
     * Logout — blacklist the current access token immediately.
     * Call this when a scanner is lost, returned, or a worker shift ends.
     *
     * POST /api/v1/auth/logout
     * Header: Authorization: Bearer <access_token>
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @RequestHeader("Authorization") String authHeader) {

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            // FIX: add to Redis blacklist — JwtAuthenticationFilter checks this on every request
            tokenBlacklistService.blacklist(token);
            log.info("Token blacklisted on logout");
        }

        return ResponseEntity.noContent().build();
    }
}