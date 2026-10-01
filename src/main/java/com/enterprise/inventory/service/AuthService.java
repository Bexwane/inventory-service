package com.enterprise.inventory.service;

import com.enterprise.inventory.dto.AuthDTO;
import com.enterprise.inventory.dto.AuthResponseDTO;
import com.enterprise.inventory.repository.UserRepository;
import com.enterprise.inventory.security.JwtUtil;
import com.enterprise.inventory.security.TokenBlacklistService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final long ACCESS_TOKEN_LIFETIME_SECONDS = 3600L;

    private final AuthenticationManager authenticationManager;
    private final UserDetailsService userDetailsService;
    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;
    private final TokenBlacklistService tokenBlacklistService;

    /**
     * Authenticates a user based on their credentials and generates access/refresh tokens.
     * 
     * @param request The login credentials (username and password).
     * @return AuthResponseDTO containing the JWTs and expiry information.
     * @throws AuthenticationException if the credentials are invalid or the user is inactive.
     */
    public AuthResponseDTO login(AuthDTO request) throws AuthenticationException {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password()));

        UserDetails userDetails = userDetailsService.loadUserByUsername(request.username());

        UUID userId = getUserId(request.username());

        String accessToken = jwtUtil.generateAccessToken(userDetails, userId);
        String refreshToken = jwtUtil.generateRefreshToken(userDetails);

        log.info("Login successful for user: {}", request.username());

        return new AuthResponseDTO(accessToken, refreshToken, ACCESS_TOKEN_LIFETIME_SECONDS);
    }

    /**
     * Issues a new access token using a valid, non-expired refresh token.
     * This prevents users from having to constantly log in while keeping short token lifetimes.
     * 
     * @param authHeader The Authorization header containing the Bearer refresh token.
     * @return AuthResponseDTO containing the newly generated access token.
     */
    public AuthResponseDTO refresh(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new IllegalArgumentException("Invalid or missing Authorization header.");
        }

        String refreshToken = authHeader.substring(7);

        if (!jwtUtil.isRefreshTokenValid(refreshToken)) {
            throw new IllegalArgumentException("Invalid refresh token.");
        }

        String username = jwtUtil.extractUsername(refreshToken);
        UserDetails userDetails = userDetailsService.loadUserByUsername(username);

        UUID userId = getUserId(username);

        String newAccessToken = jwtUtil.generateAccessToken(userDetails, userId);

        return new AuthResponseDTO(newAccessToken, refreshToken, ACCESS_TOKEN_LIFETIME_SECONDS);
    }

    /**
     * Logs the user out by immediately placing their current access token on a Redis/Postgres blacklist.
     * 
     * @param authHeader The Authorization header containing the Bearer access token to revoke.
     */
    public void logout(String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            tokenBlacklistService.blacklist(token);
            log.info("Token blacklisted on logout");
        }
    }

    /**
     * Helper method to map a username to their internal UUID.
     * Only active users are allowed to authenticate.
     */
    private UUID getUserId(String username) {
        return userRepository.findByUsernameAndIsActiveTrue(username)
                .map(user -> user.getId())
                .orElseThrow();
    }
}
