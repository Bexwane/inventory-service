package com.enterprise.inventory.inventory.infrastructure.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.Map;
import java.util.function.Function;

/**
 * Handles JWT creation and validation.
 *
 * FIX 1: Secret is injected from application.yml → environment variable.
 *         No hardcoded string literal anywhere in this class.
 *
 * FIX 2: Access token (short-lived) + refresh token (long-lived) pattern.
 *         Workers get a new access token via refresh — scanners never
 *         drop mid-shift due to expiry.
 *
 * FIX 3: Token type claim ("typ": "access" | "refresh") prevents a
 *         refresh token being used as an access token and vice versa.
 */
@Component
public class JwtUtil {

    private final SecretKey key;
    private final long accessExpirationMs;
    private final long refreshExpirationMs;

    // FIX: @Value reads from application.yml which reads from ${JWT_SECRET} env var
    public JwtUtil(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration-ms}") long accessExpirationMs,
            @Value("${jwt.refresh-expiration-ms}") long refreshExpirationMs) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes());
        this.accessExpirationMs = accessExpirationMs;
        this.refreshExpirationMs = refreshExpirationMs;
    }

    // ── Token generation ──────────────────────────────────────────────────────

    public String generateAccessToken(UserDetails userDetails) {
        return buildToken(userDetails.getUsername(), "access", accessExpirationMs,
                Map.of("roles", userDetails.getAuthorities().stream()
                        .map(a -> a.getAuthority()).toList()));
    }

    public String generateRefreshToken(UserDetails userDetails) {
        // FIX: refresh token carries only subject + type — no roles/claims
        //      so it cannot be used to authorise any action directly
        return buildToken(userDetails.getUsername(), "refresh", refreshExpirationMs, Map.of());
    }

    private String buildToken(String subject, String tokenType,
                              long expirationMs, Map<String, Object> extraClaims) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(subject)
                .claim("typ", tokenType)         // FIX: token type to prevent substitution
                .claims(extraClaims)
                .issuedAt(new Date(now))
                .expiration(new Date(now + expirationMs))
                .signWith(key)
                .compact();
    }

    // ── Token validation ──────────────────────────────────────────────────────

    /**
     * Validates an access token against a loaded UserDetails.
     * FIX: explicitly checks token type — a refresh token passed here is rejected.
     */
    public boolean isAccessTokenValid(String token, UserDetails userDetails) {
        try {
            Claims claims = extractAllClaims(token);
            String username = claims.getSubject();
            String tokenType = claims.get("typ", String.class);
            return username.equals(userDetails.getUsername())
                    && "access".equals(tokenType)
                    && !isExpired(claims);
        } catch (JwtException | IllegalArgumentException e) {
            // FIX: catch all JWT exceptions — malformed, tampered, expired
            return false;
        }
    }

    /**
     * Validates a refresh token (type check + expiry only — no UserDetails needed).
     */
    public boolean isRefreshTokenValid(String token) {
        try {
            Claims claims = extractAllClaims(token);
            return "refresh".equals(claims.get("typ", String.class))
                    && !isExpired(claims);
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    // ── Claims extraction ─────────────────────────────────────────────────────

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public <T> T extractClaim(String token, Function<Claims, T> resolver) {
        return resolver.apply(extractAllClaims(token));
    }

    private Claims extractAllClaims(String token) {
        // FIX: verifyWith(key) — parser will throw JwtException on bad signature or expiry
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private boolean isExpired(Claims claims) {
        return claims.getExpiration().before(new Date());
    }
}