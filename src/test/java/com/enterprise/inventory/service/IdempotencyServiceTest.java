package com.enterprise.inventory.service;

import com.enterprise.inventory.entity.IdempotencyKeyJpaEntity;
import com.enterprise.inventory.repository.IdempotencyKeyRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IdempotencyServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private IdempotencyKeyRepository idempotencyRepository;

    @Mock
    private com.enterprise.inventory.config.AppProperties appProperties;

    @InjectMocks
    private IdempotencyService idempotencyService;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        
        com.enterprise.inventory.config.AppProperties.Security security = new com.enterprise.inventory.config.AppProperties.Security();
        security.getLocks().setIdempotencyTtl(java.time.Duration.ofHours(24));
        lenient().when(appProperties.getSecurity()).thenReturn(security);
    }

    @Test
    void getOrCompute_ExecutesWhenKeyIsNew() throws Exception {
        String key = "new-key";
        String redisKey = "idempotency:" + key;
        when(valueOperations.get(redisKey)).thenReturn(null); // Not in cache
        
        when(idempotencyRepository.findById(key)).thenReturn(Optional.empty()); // Not in DB
        when(objectMapper.writeValueAsString(any())).thenReturn("\"result\"");

        Supplier<String> operation = () -> "result";

        String result = idempotencyService.getOrCompute(key, operation, String.class);

        assertEquals("result", result);
        verify(idempotencyRepository).saveAndFlush(any()); // Locked in DB
        verify(idempotencyRepository).save(any()); // Result saved to DB
        verify(valueOperations).set(eq(redisKey), eq("\"result\""), any()); // Cached in Redis
    }

    @Test
    void getOrCompute_ReturnsCachedWhenKeyExistsInRedis() throws Exception {
        String key = "cached-key";
        String redisKey = "idempotency:" + key;
        when(valueOperations.get(redisKey)).thenReturn("\"cached_result\"");
        when(objectMapper.readValue("\"cached_result\"", String.class)).thenReturn("cached_result");

        Supplier<String> operation = () -> {
            throw new RuntimeException("Should not be executed!");
        };

        String result = idempotencyService.getOrCompute(key, operation, String.class);

        assertEquals("cached_result", result);
        verify(idempotencyRepository, never()).saveAndFlush(any());
    }

    @Test
    void getOrCompute_ThrowsConflictWhenPendingInRedis() {
        String key = "pending-key";
        String redisKey = "idempotency:" + key;
        when(valueOperations.get(redisKey)).thenReturn("\"__PENDING__\"");

        Supplier<String> operation = () -> "result";

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> {
            idempotencyService.getOrCompute(key, operation, String.class);
        });
        
        assertTrue(ex.getMessage().contains("already being processed"));
        verify(idempotencyRepository, never()).saveAndFlush(any());
    }

    @Test
    void getOrCompute_ThrowsConflictWhenPendingInDb() {
        String key = "pending-db-key";
        String redisKey = "idempotency:" + key;
        when(valueOperations.get(redisKey)).thenReturn(null);
        
        IdempotencyKeyJpaEntity pendingEntity = new IdempotencyKeyJpaEntity();
        pendingEntity.setResponsePayload("\"__PENDING__\"");
        when(idempotencyRepository.findById(key)).thenReturn(Optional.of(pendingEntity));

        Supplier<String> operation = () -> "result";

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> {
            idempotencyService.getOrCompute(key, operation, String.class);
        });
        
        assertTrue(ex.getMessage().contains("already being processed"));
    }

    @Test
    void getOrCompute_ThrowsConflictWhenConcurrentInsert() {
        String key = "concurrent-key";
        String redisKey = "idempotency:" + key;
        when(valueOperations.get(redisKey)).thenReturn(null);
        when(idempotencyRepository.findById(key)).thenReturn(Optional.empty());
        
        when(idempotencyRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("duplicate"));

        Supplier<String> operation = () -> "result";

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> {
            idempotencyService.getOrCompute(key, operation, String.class);
        });
        
        assertTrue(ex.getMessage().contains("already being processed"));
    }
}
