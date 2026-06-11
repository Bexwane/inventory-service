package com.enterprise.inventory.inventory.infrastructure.config;

import com.enterprise.inventory.inventory.infrastructure.persistence.UserRepository;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
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
                .map(userEntity -> {
                    List<GrantedAuthority> authorities = new ArrayList<>();
                    
                    // Add the base role
                    authorities.add(new SimpleGrantedAuthority("ROLE_" + userEntity.getRole()));
                    
                    // Add direct permissions
                    userEntity.getPermissions().forEach(p -> 
                            authorities.add(new SimpleGrantedAuthority(p.name())));

                    return User.builder()
                            .username(userEntity.getUsername())
                            .password(userEntity.getPasswordHash())
                            .authorities(authorities)
                            .accountExpired(false)
                            .credentialsExpired(false)
                            .disabled(false)
                            .accountLocked(false)
                            .build();
                })
                .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
    }
}