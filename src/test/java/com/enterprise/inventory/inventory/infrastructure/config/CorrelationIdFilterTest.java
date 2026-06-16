package com.enterprise.inventory.inventory.infrastructure.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CorrelationIdFilterTest {

    private CorrelationIdFilter filter;
    private FilterChain filterChain;

    @BeforeEach
    void setUp() {
        filter = new CorrelationIdFilter();
        filterChain = mock(FilterChain.class);
        MDC.clear();
    }

    @Test
    void doFilterInternal_WithValidExistingId_PropagatesId() throws ServletException, IOException {
        String existingId = "req-12345-abcde";
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Correlation-ID", existingId);
        MockHttpServletResponse response = new MockHttpServletResponse();

        // Use a custom filter chain to verify MDC state during execution
        FilterChain checkingChain = (req, res) -> {
            assertEquals(existingId, MDC.get("correlationId"));
        };

        filter.doFilterInternal(request, response, checkingChain);

        assertEquals(existingId, response.getHeader("X-Correlation-ID"));
        assertNull(MDC.get("correlationId"), "MDC should be cleaned up after filter execution");
    }

    @Test
    void doFilterInternal_WithMissingId_GeneratesNewId() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        FilterChain checkingChain = (req, res) -> {
            assertNotNull(MDC.get("correlationId"));
            assertTrue(MDC.get("correlationId").length() > 10);
        };

        filter.doFilterInternal(request, response, checkingChain);

        String generatedId = response.getHeader("X-Correlation-ID");
        assertNotNull(generatedId);
        assertNull(MDC.get("correlationId"));
    }

    @Test
    void doFilterInternal_WithInvalidId_GeneratesNewId() throws ServletException, IOException {
        // Attack attempt with log injection
        String maliciousId = "123\n[ERROR] Fake log entry";
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Correlation-ID", maliciousId);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        String usedId = response.getHeader("X-Correlation-ID");
        assertNotNull(usedId);
        assertNotEquals(maliciousId, usedId, "Should reject malicious ID and generate a safe UUID");
    }
}
