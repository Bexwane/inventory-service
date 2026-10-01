package com.enterprise.inventory.filter;

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
import com.enterprise.inventory.config.AppProperties;

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

    private final AppProperties appProperties;
    private final Cache<String, Bucket> userBuckets;
    private final Cache<String, Bucket> ipBuckets;

    public RateLimitingFilter(AppProperties appProperties) {
        this.appProperties = appProperties;
        
        this.userBuckets = Caffeine.newBuilder()
                .expireAfterAccess(appProperties.getSecurity().getRateLimit().getUserBucket().getCacheDuration())
                .maximumSize(10_000)
                .build();
                
        this.ipBuckets = Caffeine.newBuilder()
                .expireAfterAccess(appProperties.getSecurity().getRateLimit().getIpBucket().getCacheDuration())
                .maximumSize(50_000)
                .build();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String path = request.getRequestURI();

        if (appProperties.getSecurity().getRateLimit().getAuthEndpoints().contains(path)) {
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

    private Bucket createUserBucket(String key) {
        AppProperties.Security.RateLimit.BucketConfig config = appProperties.getSecurity().getRateLimit().getUserBucket();
        Bandwidth limit = Bandwidth.classic(config.getBurstCapacity(), 
                Refill.greedy(config.getRefillTokens(), config.getRefillDuration()));
        return Bucket.builder().addLimit(limit).build();
    }

    private Bucket createLoginBucket(String key) {
        AppProperties.Security.RateLimit.BucketConfig config = appProperties.getSecurity().getRateLimit().getIpBucket();
        Bandwidth limit = Bandwidth.classic(config.getBurstCapacity(), 
                Refill.greedy(config.getRefillTokens(), config.getRefillDuration()));
        return Bucket.builder().addLimit(limit).build();
    }

    private String getClientIp(HttpServletRequest request) {
        return request.getRemoteAddr();
    }
}
