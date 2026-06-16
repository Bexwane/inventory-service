package com.enterprise.inventory.inventory.infrastructure.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.Refill;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Set;

/**
 * Filter that applies two-tier rate limiting:
 * 1. IP-based limiting for unauthenticated auth endpoints (brute-force protection).
 * 2. Per-user limiting (60 req/min) for all authenticated API calls.
 */
@Slf4j
@Component
public class RateLimitingFilter extends OncePerRequestFilter {

    private static final Set<String> AUTH_RATE_LIMITED_PATHS = Set.of(
            "/api/v1/auth/login",
            "/api/v1/auth/refresh"
    );

    /** Per-user bucket: 60 requests per minute with a burst of up to 20. */
    private final Cache<String, Bucket> userBuckets = Caffeine.newBuilder()
            .expireAfterAccess(Duration.ofMinutes(10))
            .maximumSize(10_000)
            .build();

    /** Per-IP bucket: 10 login attempts per minute (brute-force guard). */
    private final Cache<String, Bucket> ipBuckets = Caffeine.newBuilder()
            .expireAfterAccess(Duration.ofMinutes(10))
            .maximumSize(50_000)
            .build();

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String path = request.getRequestURI();

        // Tier 1: IP-based rate limiting on login/refresh endpoints
        if (AUTH_RATE_LIMITED_PATHS.contains(path)) {
            String clientIp = getClientIp(request);
            Bucket ipBucket = ipBuckets.get(clientIp, this::createLoginBucket);
            ConsumptionProbe probe = ipBucket.tryConsumeAndReturnRemaining(1);
            if (!probe.isConsumed()) {
                long waitSeconds = probe.getNanosToWaitForRefill() / 1_000_000_000;
                log.warn("Login rate limit exceeded for IP: {}", clientIp);
                response.addHeader("X-Rate-Limit-Retry-After-Seconds", String.valueOf(waitSeconds));
                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                response.getWriter().write("Too many login attempts. Please try again later.");
                return;
            }
            filterChain.doFilter(request, response);
            return;
        }

        // Tier 2: Per-user rate limiting for all authenticated requests
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            filterChain.doFilter(request, response);
            return;
        }

        String username = authentication.getName();
        Bucket bucket = userBuckets.get(username, this::createUserBucket);

        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        if (probe.isConsumed()) {
            response.addHeader("X-Rate-Limit-Remaining", String.valueOf(probe.getRemainingTokens()));
            filterChain.doFilter(request, response);
        } else {
            long waitSeconds = probe.getNanosToWaitForRefill() / 1_000_000_000;
            log.warn("Rate limit exceeded for user: {}", username);
            response.addHeader("X-Rate-Limit-Retry-After-Seconds", String.valueOf(waitSeconds));
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.getWriter().write("Too many requests. Please try again later.");
        }
    }

    /** 60 requests per minute for authenticated users, with a burst of 20. */
    private Bucket createUserBucket(String key) {
        Bandwidth limit = Bandwidth.classic(20, Refill.greedy(60, Duration.ofMinutes(1)));
        return Bucket.builder().addLimit(limit).build();
    }

    /** 10 attempts per minute per IP for login/refresh endpoints. */
    private Bucket createLoginBucket(String key) {
        Bandwidth limit = Bandwidth.classic(10, Refill.greedy(10, Duration.ofMinutes(1)));
        return Bucket.builder().addLimit(limit).build();
    }

    private String getClientIp(HttpServletRequest request) {
        // SECURITY FIX: Never parse X-Forwarded-For manually. It is easily spoofed by attackers.
        // Rely on Tomcat/Spring's remote address, which should be secured via proxy configuration
        // (e.g. server.forward-headers-strategy=FRAMEWORK) in a real deployment.
        return request.getRemoteAddr();
    }
}
