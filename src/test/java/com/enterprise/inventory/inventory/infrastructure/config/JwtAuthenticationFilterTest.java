package com.enterprise.inventory.inventory.infrastructure.config;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.userdetails.UserDetailsService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private UserDetailsService userDetailsService;

    @Mock
    private TokenBlacklistService tokenBlacklistService;

    @InjectMocks
    private JwtAuthenticationFilter filter;

    private FilterChain filterChain;

    @BeforeEach
    void setUp() {
        filterChain = mock(FilterChain.class);
    }

    @Test
    void doFilterInternal_RejectsBlacklistedToken() throws Exception {
        // Given
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer blacklisted-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(tokenBlacklistService.isBlacklisted("blacklisted-token")).thenReturn(true);

        // When
        filter.doFilterInternal(request, response, filterChain);

        // Then
        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("Token has been revoked"));
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    void doFilterInternal_PassesWithoutToken() throws Exception {
        // Given
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        // When
        filter.doFilterInternal(request, response, filterChain);

        // Then
        verify(filterChain).doFilter(request, response);
    }
}
