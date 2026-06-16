package com.enterprise.inventory.inventory.infrastructure.config;

import com.enterprise.inventory.inventory.infrastructure.persistence.Role;
import com.enterprise.inventory.inventory.infrastructure.persistence.UserEntity;
import com.enterprise.inventory.inventory.infrastructure.persistence.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Configuration class that initializes default user accounts in the database on startup.
 */
@Configuration
@EnableScheduling
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
                String adminPw      = getEnvOrFail("SEED_ADMIN_PASSWORD");
                String supervisorPw = getEnvOrFail("SEED_SUPERVISOR_PASSWORD");
                String workerPassword = getEnvOrFail("SEED_WORKER_PASSWORD");

                createUser(userRepository, passwordEncoder, "EMP-001", "admin",      adminPw,        Role.MANAGER, true);
                createUser(userRepository, passwordEncoder, "EMP-002", "supervisor", supervisorPw,   Role.SUPERVISOR, true);
                createUser(userRepository, passwordEncoder, "EMP-003", "worker1",    workerPassword, Role.WORKER, true);
                createUser(userRepository, passwordEncoder, "EMP-004", "worker2",    workerPassword, Role.WORKER, true);

                log.info("Seed accounts created from environment variables.");
                log.info("===========================");
            }

            if (userRepository.findByUsername("worker3").isEmpty()) {
                String workerPassword = getEnvOrFail("SEED_WORKER_PASSWORD");
                createUser(userRepository, passwordEncoder, "EMP-005", "worker3", workerPassword, Role.PICKER_ONLY, true);
            }
            if (userRepository.findByUsername("worker4").isEmpty()) {
                String workerPassword = getEnvOrFail("SEED_WORKER_PASSWORD");
                createUser(userRepository, passwordEncoder, "EMP-006", "worker4", workerPassword, Role.WORKER, false);
            }
        };
    }

    /**
     * Reads a required credential from environment variables.
     * Falls back to a local-dev default only if the env var is truly absent,
     * and logs a loud warning so it's never silently used in production.
     */
    private String getEnvOrFail(String envVar) {
        String value = System.getenv(envVar);
        if (value != null && !value.isBlank()) {
            return value;
        }
        log.warn("⚠️  Environment variable '{}' is not set. Using insecure local-dev default. DO NOT deploy to production without setting this variable!", envVar);
        return "changeme-local-dev-only";
    }

    private void createUser(UserRepository repo, PasswordEncoder encoder,
                            String employeeId, String username, String password, Role role, boolean isActive) {
        UserEntity user = new UserEntity();
        user.setEmployeeId(employeeId);
        user.setUsername(username);
        user.setPasswordHash(encoder.encode(password));
        user.setRole(role);
        user.setActive(isActive);
        repo.save(user);
    }
}
