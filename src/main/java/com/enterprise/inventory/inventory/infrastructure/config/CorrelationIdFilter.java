package com.enterprise.inventory.inventory.infrastructure.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Assigns a unique correlation ID to every inbound request.
 *
 * WHY THIS EXISTS:
 * When a scanner shows an error, operations staff need to find that
 * exact request in the logs. Without a correlation ID every log line
 * looks the same and tracing one request is impossible.
 *
 * HOW IT WORKS:
 * 1. Reads X-Correlation-ID header from the client if present (scanner apps
 *    can generate their own IDs so you can trace end-to-end).
 * 2. Otherwise generates a new UUID.
 * 3. Puts it in SLF4J MDC — the logging pattern in application.yml
 *    prints %X{correlationId} on every line automatically.
 * 4. Adds it to the response header so the client can log it too.
 * 5. Clears MDC after the request to prevent leakage to the next request
 *    on the same thread (thread pool reuse).
 *
 * Runs before the JWT filter (@Order(1)) so even unauthenticated
 * requests like failed logins get a trace ID.
 */
@Component
@Order(1)
public class CorrelationIdFilter extends OncePerRequestFilter {

    private static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
    private static final String MDC_KEY = "correlationId";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String correlationId = request.getHeader(CORRELATION_ID_HEADER);

        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }

        MDC.put(MDC_KEY, correlationId);
        // FIX: echo the ID back so the scanner can log "request ABC failed" and
        //      the server log also says "request ABC" — perfect traceability
        response.setHeader(CORRELATION_ID_HEADER, correlationId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            // FIX: always clear MDC — Tomcat thread pools reuse threads
            //      without this, the next request gets the previous correlation ID
            MDC.remove(MDC_KEY);
        }
    }
}