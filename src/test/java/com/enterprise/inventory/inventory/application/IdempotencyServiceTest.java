package com.enterprise.inventory.inventory.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

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

    @InjectMocks
    private IdempotencyService idempotencyService;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void getOrCompute_ExecutesWhenKeyIsNew() throws Exception {
        // Given
        String key = "new-key";
        String redisKey = "idempotency:" + key;
        when(valueOperations.get(redisKey)).thenReturn(null); // Not in cache
        when(valueOperations.setIfAbsent(eq(redisKey), eq("__PENDING__"), any())).thenReturn(true);
        when(objectMapper.writeValueAsString(any())).thenReturn("\"result\"");

        Supplier<String> operation = () -> "result";

        // When
        String result = idempotencyService.getOrCompute(key, operation, String.class);

        // Then
        assertEquals("result", result);
        verify(valueOperations).set(eq(redisKey), eq("\"result\""), any());
    }

    @Test
    void getOrCompute_ReturnsCachedWhenKeyExists() throws Exception {
        // Given
        String key = "cached-key";
        String redisKey = "idempotency:" + key;
        when(valueOperations.get(redisKey)).thenReturn("\"cached_result\"");
        when(objectMapper.readValue("\"cached_result\"", String.class)).thenReturn("cached_result");

        Supplier<String> operation = () -> {
            throw new RuntimeException("Should not be executed!");
        };

        // When
        String result = idempotencyService.getOrCompute(key, operation, String.class);

        // Then
        assertEquals("cached_result", result);
        verify(valueOperations, never()).setIfAbsent(anyString(), anyString(), any());
    }

    @Test
    void getOrCompute_ThrowsConflictWhenPending() {
        // Given
        String key = "pending-key";
        String redisKey = "idempotency:" + key;
        when(valueOperations.get(redisKey)).thenReturn("__PENDING__");

        Supplier<String> operation = () -> "result";

        // When & Then
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> {
            idempotencyService.getOrCompute(key, operation, String.class);
        });
        
        assertTrue(ex.getMessage().contains("already being processed"));
        verify(valueOperations, never()).setIfAbsent(anyString(), anyString(), any());
    }
}
