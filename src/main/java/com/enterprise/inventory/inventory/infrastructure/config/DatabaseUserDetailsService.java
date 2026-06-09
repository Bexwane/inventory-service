package com.enterprise.inventory.inventory.infrastructure.config;

import com.enterprise.inventory.inventory.infrastructure.persistence.UserRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Loads warehouse workers from PostgreSQL.
 *
 * WHY THIS EXISTS:
 * The original code used InMemoryUserDetailsManager with one hardcoded user.
 * That means adding an employee requires a code change + redeployment.
 * This service loads from the users table — adding a worker is an INSERT.
 *
 * The users table has: id, employee_id, username, password_hash, role, is_active.
 * Roles map to Spring Security authorities: ROLE_WORKER, ROLE_SUPERVISOR, ROLE_MANAGER.
 */
@Service
@Transactional(readOnly = true)
public class DatabaseUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public DatabaseUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return userRepository.findByUsernameAndIsActiveTrue(username)
                .map(userEntity -> User.builder()
                        .username(userEntity.getUsername())
                        .password(userEntity.getPasswordHash())    // already BCrypt-hashed in DB
                        // FIX: role from DB — WORKER, SUPERVISOR, or MANAGER
                        .authorities(List.of(new SimpleGrantedAuthority("ROLE_" + userEntity.getRole())))
                        .accountExpired(false)
                        .credentialsExpired(false)
                        // FIX: isActive flag — deactivating a user in DB locks them out immediately
                        .disabled(false)
                        .accountLocked(false)
                        .build())
                // FIX: generic message — don't reveal whether the username exists
                .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
    }
}