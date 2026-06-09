package com.enterprise.inventory.inventory.infrastructure.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Validates JWT on every secured request.
 *
 * FIX 1: Uses isAccessTokenValid() which also checks token type.
 *         A refresh token cannot be used as an access token.
 *
 * FIX 2: Checks Redis blacklist before accepting any token.
 *         This is how token revocation works — when a scanner is
 *         lost or a worker is terminated, their token ID is added
 *         to the blacklist and this filter rejects it immediately,
 *         even if the token hasn't expired yet.
 *
 * FIX 3: All JWT exceptions caught silently — no stack traces leaked
 *         to the client. The request just proceeds unauthenticated
 *         and Spring Security handles the 401.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BLACKLIST_PREFIX = "jwt:blacklist:";

    private final JwtUtil jwtUtil;
    private final UserDetailsService userDetailsService;
    private final StringRedisTemplate redisTemplate;

    public JwtAuthenticationFilter(JwtUtil jwtUtil,
                                   UserDetailsService userDetailsService,
                                   StringRedisTemplate redisTemplate) {
        this.jwtUtil = jwtUtil;
        this.userDetailsService = userDetailsService;
        this.redisTemplate = redisTemplate;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        final String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        final String jwt = authHeader.substring(7);

        try {
            // FIX: check blacklist first — revoked tokens rejected before any DB call
            if (isBlacklisted(jwt)) {
                filterChain.doFilter(request, response);
                return;
            }

            final String username = jwtUtil.extractUsername(jwt);

            if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                UserDetails userDetails = userDetailsService.loadUserByUsername(username);

                // FIX: isAccessTokenValid checks type claim — refresh tokens rejected here
                if (jwtUtil.isAccessTokenValid(jwt, userDetails)) {
                    UsernamePasswordAuthenticationToken authToken =
                            new UsernamePasswordAuthenticationToken(
                                    userDetails, null, userDetails.getAuthorities());
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            }
        } catch (Exception e) {
            // FIX: swallow exception — don't expose internals, just treat as unauthenticated
            // The request continues and Spring Security's 401 handler takes over
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }

    private boolean isBlacklisted(String token) {
        try {
            // We store the token itself (or its JTI claim) as the Redis key
            return Boolean.TRUE.equals(redisTemplate.hasKey(BLACKLIST_PREFIX + token));
        } catch (Exception e) {
            // FIX: if Redis is down, fail open (allow) to prevent full outage
            // This is an acceptable tradeoff — log it and alert via monitoring
            logger.warn("Redis unavailable for blacklist check — failing open");
            return false;
        }
    }
}