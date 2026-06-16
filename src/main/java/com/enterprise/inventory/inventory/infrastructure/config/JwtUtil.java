package com.enterprise.inventory.inventory.infrastructure.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * Utility class to generate, parse, and validate JSON Web Tokens for authentication.
 */
@Component
public class JwtUtil {

    private final PrivateKey privateKey;
    private final PublicKey publicKey;
    private final long accessExpirationMs;
    private final long refreshExpirationMs;

    public JwtUtil(
            @Value("${jwt.expiration-ms}") long accessExpirationMs,
            @Value("${jwt.refresh-expiration-ms}") long refreshExpirationMs) {
        this.accessExpirationMs = accessExpirationMs;
        this.refreshExpirationMs = refreshExpirationMs;
        
        try {
            java.security.KeyPairGenerator keyPairGenerator = java.security.KeyPairGenerator.getInstance("RSA");
            keyPairGenerator.initialize(2048);
            KeyPair keyPair = keyPairGenerator.generateKeyPair();
            this.privateKey = keyPair.getPrivate();
            this.publicKey = keyPair.getPublic();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new RuntimeException("Failed to generate RSA keys", e);
        }
    }

    public String generateAccessToken(UserDetails userDetails, java.util.UUID userId) {
        return buildToken(
                userDetails.getUsername(), 
                "access", 
                accessExpirationMs,
                Map.of(
                        "roles",  userDetails.getAuthorities().stream().map(a -> a.getAuthority()).toList(),
                        "userId", userId.toString()
                )
        );
    }

    public String generateRefreshToken(UserDetails userDetails) {
        return buildToken(userDetails.getUsername(), "refresh", refreshExpirationMs, Map.of());
    }

    private String buildToken(String subject, 
                              String tokenType,
                              long expirationMs, 
                              Map<String, Object> extraClaims) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(subject)
                .id(UUID.randomUUID().toString())
                .claim("typ", tokenType)
                .claims(extraClaims)
                .issuedAt(new Date(now))
                .expiration(new Date(now + expirationMs))
                .signWith(privateKey)
                .compact();
    }

    public boolean isAccessTokenValid(String token, UserDetails userDetails) {
        try {
            Claims claims = extractAllClaims(token);
            String username = claims.getSubject();
            String tokenType = claims.get("typ", String.class);
            return username.equals(userDetails.getUsername())
                    && "access".equals(tokenType)
                    && !isExpired(claims);
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public boolean isRefreshTokenValid(String token) {
        try {
            Claims claims = extractAllClaims(token);
            return "refresh".equals(claims.get("typ", String.class))
                    && !isExpired(claims);
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public java.util.UUID extractUserId(String token) {
        String userId = extractClaim(token, c -> c.get("userId", String.class));
        return userId != null ? java.util.UUID.fromString(userId) : null;
    }

    public String extractJti(String token) {
        return extractClaim(token, Claims::getId);
    }

    public <T> T extractClaim(String token, Function<Claims, T> resolver) {
        return resolver.apply(extractAllClaims(token));
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(publicKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private boolean isExpired(Claims claims) {
        return claims.getExpiration().before(new Date());
    }
}