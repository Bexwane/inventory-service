package com.enterprise.inventory.service;

import com.enterprise.inventory.entity.IdempotencyKeyJpaEntity;
import com.enterprise.inventory.repository.IdempotencyKeyRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.function.Supplier;
import com.fasterxml.jackson.core.type.TypeReference;

/**
 * Service to guarantee idempotency of incoming requests.
 * Uses PostgreSQL as the System of Record lock coordinator to prevent race conditions,
 * while using Redis strictly as a high-speed cache for the responses.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private static final String PREFIX          = "idempotency:";
    private static final String PENDING         = "\"__PENDING__\"";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper        objectMapper;
    private final IdempotencyKeyRepository idempotencyRepository;
    private final com.enterprise.inventory.config.AppProperties appProperties;

    /**
     * Executes an operation idempotently, returning the cached response if it has already been processed.
     * This signature is used for operations returning standard class types.
     * 
     * @param idempotencyKey A unique string identifying this specific request (e.g., from an HTTP header).
     * @param operation The business logic to execute if this is the first time seeing this key.
     * @param responseType The Class type to deserialize the cached JSON response into.
     * @return The result of the operation, either freshly computed or from the cache.
     */
    public <T> T getOrCompute(String idempotencyKey, Supplier<T> operation, Class<T> responseType) {
        return executeWithIdempotency(idempotencyKey, operation, json -> {
            try {
                return objectMapper.readValue(json, responseType);
            } catch (Exception e) {
                throw new RuntimeException("Failed to deserialize idempotency response", e);
            }
        });
    }

    /**
     * Executes an operation idempotently, returning the cached response if it has already been processed.
     * This signature is used for operations returning complex generic types (like List<InventoryResponseDTO>).
     * 
     * @param idempotencyKey A unique string identifying this specific request.
     * @param operation The business logic to execute.
     * @param typeRef The TypeReference used to deserialize complex generic JSON safely.
     * @return The result of the operation.
     */
    public <T> T getOrCompute(String idempotencyKey, Supplier<T> operation, TypeReference<T> typeRef) {
        return executeWithIdempotency(idempotencyKey, operation, json -> {
            try {
                return objectMapper.readValue(json, typeRef);
            } catch (Exception e) {
                throw new RuntimeException("Failed to deserialize idempotency response", e);
            }
        });
    }

    /**
     * Core orchestration logic for the hybrid Postgres/Redis idempotency pattern.
     * Safely locks the operation to prevent race conditions (like two workers picking the exact same item),
     * and guarantees that retrying a failed network request will never duplicate the operation.
     */
    private <T> T executeWithIdempotency(String idempotencyKey, Supplier<T> operation, java.util.function.Function<String, T> deserializer) {
        String redisKey = PREFIX + idempotencyKey;

        try {
            String cached = redisTemplate.opsForValue().get(redisKey);
            if (cached != null && !PENDING.equals(cached)) {
                log.debug("Idempotency cache hit for key: {}", idempotencyKey);
                return deserializer.apply(cached);
            }
            if (PENDING.equals(cached)) {
                throw new IllegalStateException("A request with this idempotency key is already being processed.");
            }
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Redis unavailable for idempotency. Checking Postgres fallback: {}", e.getMessage());
        }

        var dbOpt = idempotencyRepository.findById(idempotencyKey);
        if (dbOpt.isPresent()) {
            IdempotencyKeyJpaEntity dbKey = dbOpt.get();
            if (PENDING.equals(dbKey.getResponsePayload())) {
                throw new IllegalStateException("A request with this idempotency key is already being processed.");
            }
            return deserializer.apply(dbKey.getResponsePayload());
        }

        try {
            IdempotencyKeyJpaEntity pendingEntity = IdempotencyKeyJpaEntity.builder()
                    .key(idempotencyKey)
                    .responsePayload(PENDING)
                    .expiresAt(Instant.now().plus(appProperties.getSecurity().getLocks().getIdempotencyTtl()))
                    .build();
            idempotencyRepository.saveAndFlush(pendingEntity);
        } catch (DataIntegrityViolationException e) {
            throw new IllegalStateException("A request with this idempotency key is already being processed.");
        }

        try {
            T result = operation.get();
            String jsonResponse = objectMapper.writeValueAsString(result);

            IdempotencyKeyJpaEntity finishedEntity = IdempotencyKeyJpaEntity.builder()
                    .key(idempotencyKey)
                    .responsePayload(jsonResponse)
                    .expiresAt(Instant.now().plus(appProperties.getSecurity().getLocks().getIdempotencyTtl()))
                    .build();
            idempotencyRepository.save(finishedEntity);

            try {
                redisTemplate.opsForValue().set(redisKey, jsonResponse, appProperties.getSecurity().getLocks().getIdempotencyTtl());
            } catch (Exception e) {
                log.warn("Failed to push idempotency result to Redis, but safely stored in DB: {}", e.getMessage());
            }

            return result;
        } catch (Exception e) {
            idempotencyRepository.deleteById(idempotencyKey);
            throw (e instanceof RuntimeException re) ? re : new RuntimeException(e);
        }
    }
}
