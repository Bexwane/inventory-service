package com.enterprise.inventory.security;

import com.enterprise.inventory.repository.UserRepository;
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
 * UserDetailsService implementation that loads user authentication and authorization details from PostgreSQL.
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
                    
                    authorities.add(new SimpleGrantedAuthority("ROLE_" + userEntity.getRole().name()));
                    
                    userEntity.getPermissions().forEach(p -> 
                            authorities.add(new SimpleGrantedAuthority(p.name())));

                    return User.builder()
                            .username(userEntity.getUsername())
                            .password(userEntity.getPasswordHash())
                            .authorities(authorities)
                            .accountExpired(false)
                            .credentialsExpired(false)
                            .disabled(false)
                            .accountLocked(!userEntity.isActive())
                            .build();
                })
                .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
    }
}