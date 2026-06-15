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
 * Service to guarantee idempotency of incoming requests by caching results in Redis.
 *
 * Uses an atomic SET NX (set-if-not-exists) pattern to prevent the race condition
 * where two concurrent requests with the same idempotency key would both see a cache
 * miss and execute the operation twice. The key is reserved with a PENDING sentinel
 * before execution, so any duplicate request sees the key already exists and waits
 * or returns a 409 conflict.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private static final String PREFIX          = "idempotency:";
    private static final String PENDING         = "__PENDING__";
    private static final Duration TTL           = Duration.ofHours(24);
    private static final Duration PENDING_TTL   = Duration.ofSeconds(30);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper        objectMapper;

    public <T> T getOrCompute(String idempotencyKey, Supplier<T> operation, Class<T> responseType) {
        String redisKey = PREFIX + idempotencyKey;

        // 1. Check for a cached result first
        try {
            String cached = redisTemplate.opsForValue().get(redisKey);
            if (cached != null && !PENDING.equals(cached)) {
                log.debug("Idempotency cache hit for key: {}", idempotencyKey);
                return objectMapper.readValue(cached, responseType);
            }
            if (PENDING.equals(cached)) {
                throw new IllegalStateException("A request with this idempotency key is already being processed. Please retry shortly.");
            }
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Idempotency cache read failed — processing anyway: {}", e.getMessage());
        }

        // 2. Atomically reserve the key with a PENDING sentinel (SET NX)
        Boolean reserved = redisTemplate.opsForValue().setIfAbsent(redisKey, PENDING, PENDING_TTL);
        if (!Boolean.TRUE.equals(reserved)) {
            // Another thread/instance already reserved this key concurrently
            throw new IllegalStateException("A request with this idempotency key is already being processed. Please retry shortly.");
        }

        // 3. Execute the operation now that we hold the lock
        try {
            T result = operation.get();
            redisTemplate.opsForValue().set(redisKey, objectMapper.writeValueAsString(result), TTL);
            return result;
        } catch (Exception e) {
            // Release the lock on failure so the client can retry with the same key
            redisTemplate.delete(redisKey);
            throw (e instanceof RuntimeException re) ? re : new RuntimeException(e);
        }
    }

    public <T> T getOrCompute(String idempotencyKey, Supplier<T> operation, TypeReference<T> typeRef) {
        String redisKey = PREFIX + idempotencyKey;

        try {
            String cached = redisTemplate.opsForValue().get(redisKey);
            if (cached != null && !PENDING.equals(cached)) {
                log.debug("Idempotency cache hit for key: {}", idempotencyKey);
                return objectMapper.readValue(cached, typeRef);
            }
            if (PENDING.equals(cached)) {
                throw new IllegalStateException("A request with this idempotency key is already being processed. Please retry shortly.");
            }
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Idempotency cache read failed — processing anyway: {}", e.getMessage());
        }

        Boolean reserved = redisTemplate.opsForValue().setIfAbsent(redisKey, PENDING, PENDING_TTL);
        if (!Boolean.TRUE.equals(reserved)) {
            throw new IllegalStateException("A request with this idempotency key is already being processed. Please retry shortly.");
        }

        try {
            T result = operation.get();
            redisTemplate.opsForValue().set(redisKey, objectMapper.writeValueAsString(result), TTL);
            return result;
        } catch (Exception e) {
            redisTemplate.delete(redisKey);
            throw (e instanceof RuntimeException re) ? re : new RuntimeException(e);
        }
    }
}
