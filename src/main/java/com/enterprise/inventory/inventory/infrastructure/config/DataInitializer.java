package com.enterprise.inventory.inventory.infrastructure.config;

import com.enterprise.inventory.inventory.infrastructure.persistence.UserEntity;
import com.enterprise.inventory.inventory.infrastructure.persistence.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class DataInitializer {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    @Bean
    public CommandLineRunner initDefaultUsers(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        return args -> {
            if (userRepository.count() == 0) {
                createUser(userRepository, passwordEncoder, "EMP-001", "admin",      "admin123",      "MANAGER");
                createUser(userRepository, passwordEncoder, "EMP-002", "supervisor", "supervisor123", "SUPERVISOR");
                createUser(userRepository, passwordEncoder, "EMP-003", "worker1",    "worker123",     "WORKER");
                createUser(userRepository, passwordEncoder, "EMP-004", "worker2",    "worker456",     "WORKER");

                log.info("=== Demo accounts ready ===");
                log.info("  admin       / admin123       (MANAGER)");
                log.info("  supervisor  / supervisor123  (SUPERVISOR)");
                log.info("  worker1     / worker123      (WORKER)");
                log.info("  worker2     / worker456      (WORKER)");
                log.info("===========================");
            }
            
            // Dynamically add extra test workers if missing
            if (userRepository.findByUsernameAndIsActiveTrue("worker3").isEmpty()) {
                createUser(userRepository, passwordEncoder, "EMP-005", "worker3", "worker123", "VIEWER_ONLY");
            }
            if (userRepository.findByUsernameAndIsActiveTrue("worker4").isEmpty()) {
                createUser(userRepository, passwordEncoder, "EMP-006", "worker4", "worker123", "SUSPENDED");
            }
        };
    }

    private void createUser(UserRepository repo, PasswordEncoder encoder,
                            String employeeId, String username, String password, String role) {
        UserEntity u = new UserEntity();
        u.setEmployeeId(employeeId);
        u.setUsername(username);
        u.setPasswordHash(encoder.encode(password));
        u.setRole(role);
        u.setActive(true);
        repo.save(u);
    }
}
