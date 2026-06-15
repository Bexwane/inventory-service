package com.enterprise.inventory.inventory.web.controller;

import com.enterprise.inventory.inventory.infrastructure.config.JwtUtil;
import com.enterprise.inventory.inventory.infrastructure.persistence.UserRepository;
import com.enterprise.inventory.inventory.web.dto.AuthDTO;
import com.enterprise.inventory.inventory.web.dto.AuthResponseDTO;
import com.enterprise.inventory.inventory.web.dto.ErrorResponseDTO;
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
 * REST controller handling authentication endpoints, including user login, token refresh, and logout blacklisting.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final UserDetailsService    userDetailsService;
    private final UserRepository        userRepository;
    private final JwtUtil               jwtUtil;
    private final TokenBlacklistService tokenBlacklistService;

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody AuthDTO request) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.username(), request.password()));
        } catch (AuthenticationException ex) {
            log.warn("Failed login attempt for username: {}", request.username());
            return ResponseEntity.status(401)
                    .body(new ErrorResponseDTO("INVALID_CREDENTIALS", "Invalid username or password.", null));
        }

        UserDetails userDetails = userDetailsService.loadUserByUsername(request.username());

        java.util.UUID userId = userRepository.findByUsernameAndIsActiveTrue(request.username())
                .map(u -> u.getId())
                .orElseThrow();

        String accessToken  = jwtUtil.generateAccessToken(userDetails, userId);
        String refreshToken = jwtUtil.generateRefreshToken(userDetails);

        log.info("Login successful for user: {}", request.username());

        return ResponseEntity.ok(new AuthResponseDTO(
                accessToken,
                refreshToken,
                3600L
                ));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponseDTO> refresh(
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

        java.util.UUID userId = userRepository.findByUsernameAndIsActiveTrue(username)
                .map(u -> u.getId())
                .orElseThrow();

        String newAccessToken = jwtUtil.generateAccessToken(userDetails, userId);

        return ResponseEntity.ok(new AuthResponseDTO(
                newAccessToken,
                refreshToken,
                3600L
        ));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @RequestHeader("Authorization") String authHeader) {

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            tokenBlacklistService.blacklist(token);
            log.info("Token blacklisted on logout");
        }

        return ResponseEntity.noContent().build();
    }
}