package com.enterprise.inventory.config;

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
import com.enterprise.inventory.security.JwtAuthenticationFilter;
import com.enterprise.inventory.filter.CorrelationIdFilter;
import com.enterprise.inventory.filter.RateLimitingFilter;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

/**
 * Security configuration for the application, establishing filter chains, password encoding, and authentication management.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;
    private final CorrelationIdFilter correlationIdFilter;
    private final RateLimitingFilter rateLimitingFilter;
    private final AppProperties appProperties;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthFilter,
                          CorrelationIdFilter correlationIdFilter,
                          RateLimitingFilter rateLimitingFilter,
                          AppProperties appProperties) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.correlationIdFilter = correlationIdFilter;
        this.rateLimitingFilter = rateLimitingFilter;
        this.appProperties = appProperties;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(headers -> headers
                        .frameOptions(f -> f.deny())
                        .contentTypeOptions(c -> {})
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(31536000))
                        .referrerPolicy(r ->
                                r.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(appProperties.getApi().getBasePath() + appProperties.getApi().getAuth().getBase() + appProperties.getApi().getAuth().getLogin()).permitAll()
                        .requestMatchers(appProperties.getApi().getBasePath() + appProperties.getApi().getAuth().getBase() + appProperties.getApi().getAuth().getRefresh()).permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers("/actuator/**").hasAuthority("CAN_MANAGE_USERS")
                        .anyRequest().authenticated()
                )
                .addFilterBefore(correlationIdFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(rateLimitingFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(appProperties.getSecurity().getPassword().getBcryptStrength());
    }

    @Bean
    public AuthenticationManager authenticationManager(
            UserDetailsService userDetailsService,
            PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        provider.setHideUserNotFoundExceptions(true);
        return new ProviderManager(provider);
    }
}