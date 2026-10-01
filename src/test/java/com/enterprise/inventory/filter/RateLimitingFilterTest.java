package com.enterprise.inventory.filter;

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
        com.enterprise.inventory.config.AppProperties props = new com.enterprise.inventory.config.AppProperties();
        filter = new RateLimitingFilter(props);
        filterChain = mock(FilterChain.class);
        SecurityContextHolder.clearContext();
    }

    @Test
    void testLoginRateLimiting() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        request.setRemoteAddr("192.168.1.1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        for (int i = 0; i < 10; i++) {
            filter.doFilterInternal(request, response, filterChain);
        }

        verify(filterChain, times(10)).doFilter(request, response);
        assertEquals(HttpStatus.OK.value(), response.getStatus()); // default mock response status

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

        for (int i = 0; i < 30; i++) {
            filter.doFilterInternal(request, response, filterChain);
        }

        assertEquals(HttpStatus.TOO_MANY_REQUESTS.value(), response.getStatus());
    }
}
