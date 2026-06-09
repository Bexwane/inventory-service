package com.enterprise.inventory.inventory.infrastructure.config;

import com.enterprise.inventory.inventory.infrastructure.persistence.UserEntity;
import com.enterprise.inventory.inventory.infrastructure.persistence.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.fasterxml.jackson.databind.ObjectMapper;

@Configuration
public class DataInitializer {

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    @Bean
    public CommandLineRunner initDefaultUsers(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        return args -> {
            if (userRepository.count() == 0) {
                UserEntity admin = new UserEntity();
                admin.setEmployeeId("EMP-001");
                admin.setUsername("admin");
                admin.setPasswordHash(passwordEncoder.encode("admin123"));
                admin.setRole("MANAGER");
                admin.setActive(true);
                userRepository.save(admin);

                UserEntity worker = new UserEntity();
                worker.setEmployeeId("EMP-002");
                worker.setUsername("worker");
                worker.setPasswordHash(passwordEncoder.encode("worker123"));
                worker.setRole("WORKER");
                worker.setActive(true);
                userRepository.save(worker);
                
                System.out.println("=========================================================");
                System.out.println("DEFAULT USERS CREATED:");
                System.out.println("Username: admin  | Password: admin123  | Role: MANAGER");
                System.out.println("Username: worker | Password: worker123 | Role: WORKER");
                System.out.println("=========================================================");
            }
        };
    }
}
