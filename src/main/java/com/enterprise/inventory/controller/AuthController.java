package com.enterprise.inventory.controller;

import com.enterprise.inventory.service.AuthService;
import com.enterprise.inventory.dto.AuthDTO;
import com.enterprise.inventory.dto.AuthResponseDTO;
import com.enterprise.inventory.dto.ErrorResponseDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST controller handling authentication endpoints, including user login, token refresh, and logout blacklisting.
 */
@Slf4j
@RestController
@RequestMapping("${app.api.base-path}${app.api.auth.base}")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("${app.api.auth.login}")
    public ResponseEntity<?> login(@Valid @RequestBody AuthDTO request) {
        try {
            AuthResponseDTO response = authService.login(request);
            return ResponseEntity.ok(response);
        } catch (AuthenticationException ex) {
            log.warn("Failed login attempt for username: {}", request.username());
            return ResponseEntity.status(401)
                    .body(new ErrorResponseDTO("INVALID_CREDENTIALS", "Invalid username or password.", null));
        }
    }

    @PostMapping("${app.api.auth.refresh}")
    public ResponseEntity<AuthResponseDTO> refresh(
            @RequestHeader("Authorization") String authHeader) {

        try {
            AuthResponseDTO response = authService.refresh(authHeader);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(401).build();
        }
    }

    @PostMapping("${app.api.auth.logout}")
    public ResponseEntity<Void> logout(
            @RequestHeader("Authorization") String authHeader) {

        authService.logout(authHeader);

        return ResponseEntity.noContent().build();
    }
}