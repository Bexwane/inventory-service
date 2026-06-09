package com.enterprise.inventory.inventory.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

/**
 * Security configuration.
 *
 * FIX 1: InMemoryUserDetailsManager removed entirely.
 *         Users are loaded from PostgreSQL via DatabaseUserDetailsService.
 *         Adding/removing workers is a database operation, not a code change.
 *
 * FIX 2: Three roles defined — WORKER, SUPERVISOR, MANAGER.
 *         Endpoint access is controlled per role.
 *         @EnableMethodSecurity allows @PreAuthorize at method level for fine-grained control.
 *
 * FIX 3: Security response headers added — prevents clickjacking, sniffing, etc.
 *
 * FIX 4: Correlation ID filter runs before JWT filter so every request gets a trace ID.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;
    private final CorrelationIdFilter correlationIdFilter;
    private final RateLimitingFilter rateLimitingFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthFilter,
                          CorrelationIdFilter correlationIdFilter,
                          RateLimitingFilter rateLimitingFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.correlationIdFilter = correlationIdFilter;
        this.rateLimitingFilter = rateLimitingFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // stateless API — CSRF not applicable, but keep headers
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // FIX: security response headers
                .headers(headers -> headers
                        .frameOptions(f -> f.deny())                           // prevent clickjacking
                        .contentTypeOptions(c -> {})                           // no-sniff
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(31536000))
                        .referrerPolicy(r ->
                                r.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                )

                // ── Endpoint access rules ──────────────────────────────────────
                .authorizeHttpRequests(auth -> auth
                        // public: login and refresh
                        .requestMatchers("/api/v1/auth/login").permitAll()
                        .requestMatchers("/api/v1/auth/refresh").permitAll()
                        // actuator health is public; other actuator endpoints require MANAGER
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers("/actuator/**").hasRole("MANAGER")

                        // FIX: role-based endpoint control
                        // any authenticated worker can query inventory
                        .requestMatchers(HttpMethod.GET, "/api/v1/inventory/**").authenticated()
                        // receiving stock = WORKER or above
                        .requestMatchers(HttpMethod.POST, "/api/v1/inventory/receive").hasAnyRole("WORKER", "SUPERVISOR", "MANAGER")
                        // picking requires WORKER or above
                        .requestMatchers(HttpMethod.POST, "/api/v1/inventory/pick/**").hasAnyRole("WORKER", "SUPERVISOR", "MANAGER")
                        // stock adjustments are SUPERVISOR only
                        .requestMatchers(HttpMethod.POST, "/api/v1/inventory/adjust").hasAnyRole("SUPERVISOR", "MANAGER")
                        // force-override putaway is SUPERVISOR only
                        .requestMatchers(HttpMethod.POST, "/api/v1/putaway/force").hasAnyRole("SUPERVISOR", "MANAGER")

                        .anyRequest().authenticated()
                )

                // FIX: correlation ID runs first so every log line has a trace ID
                .addFilterBefore(correlationIdFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                // FIX: Rate limiting runs AFTER JWT auth, so we know who the user is
                .addFilterAfter(rateLimitingFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        // strength=12 is the enterprise minimum — default 10 is too weak for 2024 hardware
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public AuthenticationManager authenticationManager(
            UserDetailsService userDetailsService,
            PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        // FIX: hide whether username or password was wrong — same error either way
        provider.setHideUserNotFoundExceptions(true);
        return new ProviderManager(provider);
    }

    /*
     * NOTE: UserDetailsService bean is NOT defined here.
     * It is defined in DatabaseUserDetailsService which loads users from PostgreSQL.
     * Spring picks it up automatically via @Service — no manual wiring needed.
     */
}