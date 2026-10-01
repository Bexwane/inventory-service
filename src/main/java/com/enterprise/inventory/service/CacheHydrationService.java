package com.enterprise.inventory.service;

import com.enterprise.inventory.security.TokenBlacklistService;
import com.enterprise.inventory.repository.IdempotencyKeyRepository;
import com.enterprise.inventory.repository.ReservationLockRepository;
import com.enterprise.inventory.repository.TokenBlacklistRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/**
 * Service responsible for:
 * 1. Hydrating Redis on startup from the Postgres System of Record to prevent Silent Restarts.
 * 2. Sweeping expired data from Postgres to prevent database bloat.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CacheHydrationService {

    private final StringRedisTemplate redisTemplate;
    private final TokenBlacklistRepository tokenBlacklistRepository;
    private final ReservationLockRepository reservationLockRepository;
    private final IdempotencyKeyRepository idempotencyKeyRepository;

    private static final String JWT_PREFIX = "jwt:blacklist:";
    private static final String RES_PREFIX = "reservation:lock:%s:%s:%s:%s";

    /**
     * Triggered automatically when the Spring application finishes starting up.
     * Fetches all active tokens and locks from the Postgres database and pre-loads them into Redis.
     * This ensures that if the server crashes and restarts, Redis doesn't start empty (Amnesia),
     * which would otherwise allow attackers to use revoked tokens or workers to double-pick inventory.
     */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional(readOnly = true)
    public void hydrateCache() {
        log.info("Starting Cache Hydration from Postgres...");
        Instant now = Instant.now();
        int tokenCount = 0;
        int lockCount = 0;

        try {
            var activeTokens = tokenBlacklistRepository.findAllActive(now);
            for (var token : activeTokens) {
                Duration ttl = Duration.between(now, token.getExpiresAt());
                if (!ttl.isNegative()) {
                    redisTemplate.opsForValue().set(JWT_PREFIX + token.getJti(), "revoked", ttl);
                    tokenCount++;
                }
            }

            var activeLocks = reservationLockRepository.findAllActive(now);
            for (var lock : activeLocks) {
                Duration ttl = Duration.between(now, lock.getExpiresAt());
                if (!ttl.isNegative()) {
                    String lockKey = RES_PREFIX.formatted(
                            lock.getTaskId(),
                            lock.getSku(),
                            lock.getLocationId(),
                            lock.getContainerId() == null ? "NONE" : lock.getContainerId()
                    );
                    redisTemplate.opsForValue().set(lockKey, String.valueOf(lock.getQty()), ttl);
                    lockCount++;
                }
            }

            redisTemplate.opsForValue().set(TokenBlacklistService.CACHE_VALID_KEY, "true");
            
            log.info("Cache Hydration Complete. Loaded {} tokens and {} reservation locks.", tokenCount, lockCount);

        } catch (Exception e) {
            log.error("Cache Hydration Failed. Redis is unavailable. WMS is operating in Database-Fallback mode.", e);
        }
    }

    /**
     * Runs every hour to delete expired rows from Postgres to prevent DB bloat.
     * Redis natively deletes expired keys, but Postgres does not.
     */
    @Scheduled(cron = "${app.jobs.sweeper.cron}")
    @Transactional
    public void sweepExpiredData() {
        log.info("Running Database Sweeper...");
        Instant now = Instant.now();
        
        int tokensDeleted = tokenBlacklistRepository.deleteExpired(now);
        int locksDeleted = reservationLockRepository.deleteExpired(now);
        int idempotencyDeleted = idempotencyKeyRepository.deleteExpired(now);

        log.info("Sweeper Complete. Purged {} tokens, {} locks, {} idempotency keys.", 
                tokensDeleted, locksDeleted, idempotencyDeleted);
    }
}
