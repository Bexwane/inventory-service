// ─────────────────────────────────────────────────────────────
// FILE: TokenBlacklistService.java
// Adds revoked tokens to Redis so they are rejected on every request
// ─────────────────────────────────────────────────────────────
package com.enterprise.inventory.inventory.infrastructure.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Stores revoked JWT tokens in Redis until they would have expired naturally.
 *
 * WHY: A JWT is valid until its expiry time — even after logout.
 * If a scanner is lost, the thief has a valid token for up to 1 hour.
 * Blacklisting lets us invalidate tokens immediately.
 *
 * The key expires from Redis automatically when the token would have expired anyway,
 * so the blacklist never grows unbounded.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenBlacklistService {

    private static final String PREFIX = "jwt:blacklist:";
    // Access tokens are 1 hour — keep blacklist entry for slightly longer to be safe
    private static final Duration TTL = Duration.ofHours(2);

    private final StringRedisTemplate redisTemplate;

    public void blacklist(String token) {
        try {
            redisTemplate.opsForValue().set(PREFIX + token, "revoked", TTL);
        } catch (Exception e) {
            // Log but don't fail the logout request — the client should still clear the token locally
            log.error("Failed to blacklist token in Redis: {}", e.getMessage());
        }
    }

    public boolean isBlacklisted(String token) {
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(PREFIX + token));
        } catch (Exception e) {
            log.warn("Redis unavailable for blacklist check — failing open: {}", e.getMessage());
            return false;  // fail open to prevent full outage if Redis is down
        }
    }
}


