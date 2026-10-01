package com.enterprise.inventory.security;

import com.enterprise.inventory.entity.TokenBlacklistJpaEntity;
import com.enterprise.inventory.repository.TokenBlacklistRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TokenBlacklistServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private TokenBlacklistRepository tokenBlacklistRepository;

    @Mock
    private com.enterprise.inventory.config.AppProperties appProperties;

    @InjectMocks
    private TokenBlacklistService tokenBlacklistService;

    private final String MOCK_TOKEN = "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.mock";
    private final String MOCK_JTI = "a123b456-c789-4012-8345-f678e901a234";

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        
        com.enterprise.inventory.config.AppProperties.Security security = new com.enterprise.inventory.config.AppProperties.Security();
        security.getBlacklist().setCacheDuration(java.time.Duration.ofHours(2));
        lenient().when(appProperties.getSecurity()).thenReturn(security);
    }

    @Test
    void blacklist_savesToPostgresAndRedis() {
        when(jwtUtil.extractJti(MOCK_TOKEN)).thenReturn(MOCK_JTI);

        tokenBlacklistService.blacklist(MOCK_TOKEN);

        verify(tokenBlacklistRepository, times(1)).save(any(TokenBlacklistJpaEntity.class));
        
        verify(valueOperations, times(1)).set(eq("jwt:blacklist:" + MOCK_JTI), eq("revoked"), any());
    }

    @Test
    void isBlacklisted_cacheValid_foundInRedis() {
        when(jwtUtil.extractJti(MOCK_TOKEN)).thenReturn(MOCK_JTI);
        
        when(redisTemplate.hasKey(TokenBlacklistService.CACHE_VALID_KEY)).thenReturn(true);
        when(redisTemplate.hasKey("jwt:blacklist:" + MOCK_JTI)).thenReturn(true);

        assertTrue(tokenBlacklistService.isBlacklisted(MOCK_TOKEN));
        
        verify(tokenBlacklistRepository, never()).existsById(any());
    }

    @Test
    void isBlacklisted_redisAmnesia_cacheValidityKeyMissing_fallsBackToPostgres() {
        when(jwtUtil.extractJti(MOCK_TOKEN)).thenReturn(MOCK_JTI);
        
        when(redisTemplate.hasKey(TokenBlacklistService.CACHE_VALID_KEY)).thenReturn(false);
        
        when(tokenBlacklistRepository.existsById(UUID.fromString(MOCK_JTI))).thenReturn(true);

        assertTrue(tokenBlacklistService.isBlacklisted(MOCK_TOKEN));
        
        verify(tokenBlacklistRepository, times(1)).existsById(UUID.fromString(MOCK_JTI));
        verify(redisTemplate, never()).hasKey("jwt:blacklist:" + MOCK_JTI);
    }

    @Test
    void isBlacklisted_redisCompleteCrash_connectionRefused_fallsBackToPostgres() {
        when(jwtUtil.extractJti(MOCK_TOKEN)).thenReturn(MOCK_JTI);
        
        when(redisTemplate.hasKey(TokenBlacklistService.CACHE_VALID_KEY))
            .thenThrow(new RedisConnectionFailureException("Connection refused"));
            
        when(tokenBlacklistRepository.existsById(UUID.fromString(MOCK_JTI))).thenReturn(true);

        assertTrue(tokenBlacklistService.isBlacklisted(MOCK_TOKEN));
        
        verify(tokenBlacklistRepository, times(1)).existsById(UUID.fromString(MOCK_JTI));
    }
}
