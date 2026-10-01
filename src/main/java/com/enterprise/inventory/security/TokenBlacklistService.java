package com.enterprise.inventory.security;

import com.enterprise.inventory.entity.TokenBlacklistJpaEntity;
import com.enterprise.inventory.repository.TokenBlacklistRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Service managing JWT blacklisting in Redis and PostgreSQL to handle user logouts and token invalidation.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenBlacklistService {

    private static final String PREFIX = "jwt:blacklist:";
    public static final String CACHE_VALID_KEY = "wms:cache:valid";

    private final StringRedisTemplate redisTemplate;
    private final JwtUtil             jwtUtil;
    private final TokenBlacklistRepository tokenBlacklistRepository;
    private final com.enterprise.inventory.config.AppProperties appProperties;

    @Transactional
    public void blacklist(String token) {
        String jti = jwtUtil.extractJti(token);
        if (jti == null) {
            log.warn("Cannot blacklist token — no jti claim found (old token format?)");
            return;
        }

        TokenBlacklistJpaEntity entity = TokenBlacklistJpaEntity.builder()
                .jti(UUID.fromString(jti))
                .expiresAt(Instant.now().plus(appProperties.getSecurity().getBlacklist().getCacheDuration()))
                .build();
        tokenBlacklistRepository.save(entity);

        try {
            redisTemplate.opsForValue().set(PREFIX + jti, "revoked", appProperties.getSecurity().getBlacklist().getCacheDuration());
            log.debug("Token jti={} blacklisted in DB and Redis", jti);
        } catch (Exception e) {
            log.warn("Failed to blacklist token in Redis, but safely saved to DB: {}", e.getMessage());
        }
    }

    public boolean isBlacklisted(String token) {
        String jti = jwtUtil.extractJti(token);
        if (jti == null) return false;

        try {
            if (Boolean.TRUE.equals(redisTemplate.hasKey(CACHE_VALID_KEY))) {
                return Boolean.TRUE.equals(redisTemplate.hasKey(PREFIX + jti));
            } else {
                log.warn("Redis Cache Validity Key missing (Amnesia detected). Falling back to Database.");
            }
        } catch (Exception e) {
            log.warn("Redis unavailable for blacklist check. Falling back to Database: {}", e.getMessage());
        }

        return tokenBlacklistRepository.existsById(UUID.fromString(jti));
    }
}
