package com.enterprise.inventory.inventory.infrastructure.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.List;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class RateLimitingFilterTest {

    private RateLimitingFilter filter;
    private FilterChain filterChain;

    @BeforeEach
    void setUp() {
        filter = new RateLimitingFilter();
        filterChain = mock(FilterChain.class);
        SecurityContextHolder.clearContext();
    }

    @Test
    void doFilterInternal_AllowsAuthRequestsWithinLimit() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        request.setRemoteAddr("192.168.1.1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        for (int i = 0; i < 10; i++) {
            filter.doFilterInternal(request, response, filterChain);
        }

        // Limit is 10. All 10 should pass.
        verify(filterChain, times(10)).doFilter(request, response);
        assertEquals(HttpStatus.OK.value(), response.getStatus()); // default mock response status

        // 11th request should be blocked
        filter.doFilterInternal(request, response, filterChain);
        assertEquals(HttpStatus.TOO_MANY_REQUESTS.value(), response.getStatus());
        verify(filterChain, times(10)).doFilter(request, response); // still 10
    }

    @Test
    void doFilterInternal_AllowsAuthenticatedRequestsWithinLimit() throws ServletException, IOException {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("testuser", "password", List.of())
        );

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/inventory");
        MockHttpServletResponse response = new MockHttpServletResponse();

        // Limit burst is 20, so 30 requests should definitely hit the limit
        for (int i = 0; i < 30; i++) {
            filter.doFilterInternal(request, response, filterChain);
        }

        assertEquals(HttpStatus.TOO_MANY_REQUESTS.value(), response.getStatus());
    }
}
