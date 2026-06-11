// ─────────────────────────────────────────────────────────────
// FILE: IdempotencyService.java
// Prevents double-processing of retried scanner requests
// ─────────────────────────────────────────────────────────────
package com.enterprise.inventory.inventory.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.function.Supplier;
import com.fasterxml.jackson.core.type.TypeReference;

/**
 * Prevents double-processing when scanner apps retry requests.
 *
 * HOW IT WORKS:
 * 1. Client generates a UUID before sending the request (X-Idempotency-Key header).
 * 2. On first call: process normally, store the result in Redis with a 24h TTL.
 * 3. On retry: find the cached result in Redis, return it immediately — don't re-process.
 *
 * WHY 24 HOURS:
 * Warehouse shifts are typically 8-12 hours. A retry could come hours later
 * if the scanner was offline. 24h covers the full shift with margin.
 *
 * WHAT IF REDIS IS DOWN:
 * We fail open — process the request normally. This means a retry could
 * double-count in a Redis outage, but it's better than stopping all warehouse operations.
 * The stock_movements audit log can be used to detect and correct duplicates.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private static final String PREFIX = "idempotency:";
    private static final Duration TTL  = Duration.ofHours(24);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper        objectMapper;

    public <T> T getOrCompute(String idempotencyKey, Supplier<T> operation, Class<T> responseType) {
        String redisKey = PREFIX + idempotencyKey;

        try {
            String cached = redisTemplate.opsForValue().get(redisKey);
            if (cached != null) {
                log.debug("Idempotency cache hit for key: {}", idempotencyKey);
                // Deserialize back into the exact type — no unsafe cast
                return objectMapper.readValue(cached, responseType);
            }
        } catch (Exception e) {
            // Redis down — fall through and process normally
            log.warn("Idempotency cache read failed — processing anyway: {}", e.getMessage());
        }

        T result = operation.get();

        try {
            redisTemplate.opsForValue().set(
                    redisKey, objectMapper.writeValueAsString(result), TTL);
        } catch (Exception e) {
            // Cache write failed — not critical, result is still returned to client
            log.warn("Idempotency cache write failed: {}", e.getMessage());
        }

        return result;
    }

    public <T> T getOrCompute(String idempotencyKey, Supplier<T> operation, TypeReference<T> typeRef) {
        String redisKey = PREFIX + idempotencyKey;

        try {
            String cached = redisTemplate.opsForValue().get(redisKey);
            if (cached != null) {
                log.debug("Idempotency cache hit for key: {}", idempotencyKey);
                return objectMapper.readValue(cached, typeRef);
            }
        } catch (Exception e) {
            log.warn("Idempotency cache read failed — processing anyway: {}", e.getMessage());
        }

        T result = operation.get();

        try {
            redisTemplate.opsForValue().set(
                    redisKey, objectMapper.writeValueAsString(result), TTL);
        } catch (Exception e) {
            log.warn("Idempotency cache write failed: {}", e.getMessage());
        }

        return result;
    }
}


