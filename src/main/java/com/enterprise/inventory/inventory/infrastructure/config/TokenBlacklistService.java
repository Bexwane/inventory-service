package com.enterprise.inventory.inventory.infrastructure.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Service managing JWT blacklisting in Redis to handle user logouts and token invalidation.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenBlacklistService {

    private static final String PREFIX = "jwt:blacklist:";
    private static final Duration TTL = Duration.ofHours(2);

    private final StringRedisTemplate redisTemplate;
    private final JwtUtil             jwtUtil;

    public void blacklist(String token) {
        try {
            String jti = jwtUtil.extractJti(token);
            if (jti == null) {
                log.warn("Cannot blacklist token — no jti claim found (old token format?)");
                return;
            }
            redisTemplate.opsForValue().set(PREFIX + jti, "revoked", TTL);
            log.debug("Token jti={} blacklisted", jti);
        } catch (Exception e) {
            log.error("Failed to blacklist token in Redis: {}", e.getMessage());
        }
    }

    public boolean isBlacklisted(String token) {
        try {
            String jti = jwtUtil.extractJti(token);
            if (jti == null) return false;
            return Boolean.TRUE.equals(redisTemplate.hasKey(PREFIX + jti));
        } catch (Exception e) {
            log.error("Redis unavailable for blacklist check — failing closed: {}", e.getMessage());
            throw new org.springframework.security.authentication.AuthenticationServiceException("Authentication service temporarily unavailable");
        }
    }
}
