package com.enterprise.inventory.inventory.application;

import com.enterprise.inventory.inventory.infrastructure.persistence.UserEntity;
import com.enterprise.inventory.inventory.infrastructure.persistence.UserRepository;
import com.enterprise.inventory.inventory.web.dto.BatchPermissionDTO;
import com.enterprise.inventory.inventory.web.dto.CreateUserDTO;
import com.enterprise.inventory.inventory.web.dto.UpdateUserDTO;
import com.enterprise.inventory.inventory.web.dto.UserResponseDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service class containing business logic for user management operations.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository  userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public List<UserResponseDTO> listUsers() {
        return userRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public UserResponseDTO createUser(CreateUserDTO request) {
        if (userRepository.findByUsername(request.username()).isPresent()) {
            throw new IllegalArgumentException("Username already exists.");
        }
        if (userRepository.findByEmployeeId(request.employeeId()).isPresent()) {
            throw new IllegalArgumentException("Employee ID already exists.");
        }

        UserEntity user = new UserEntity();
        user.setEmployeeId(request.employeeId());
        user.setUsername(request.username());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(request.role());
        user.setPermissions(request.permissions());
        user.setActive(true);

        userRepository.save(user);
        log.info(
                "New user created: {} ({}) role={} permissions={}",
                request.username(), 
                request.employeeId(), 
                request.role(), 
                request.permissions().size()
        );
        return toResponse(user);
    }

    @Transactional
    public Optional<UserResponseDTO> deactivateUser(UUID id) {
        return userRepository.findById(id).map(user -> {
            user.setActive(false);
            userRepository.save(user);
            log.info("User deactivated: {}", user.getUsername());
            return toResponse(user);
        });
    }

    @Transactional
    public Optional<UserResponseDTO> activateUser(UUID id) {
        return userRepository.findById(id).map(user -> {
            user.setActive(true);
            userRepository.save(user);
            log.info("User activated: {}", user.getUsername());
            return toResponse(user);
        });
    }

    @Transactional
    public Optional<UserResponseDTO> updateUser(UUID id, UpdateUserDTO request) {
        return userRepository.findById(id).map(user -> {
            if (!user.getUsername().equals(request.username()) &&
                    userRepository.findByUsername(request.username()).isPresent()) {
                throw new IllegalArgumentException("Username already exists.");
            }
            if (!user.getEmployeeId().equals(request.employeeId()) &&
                    userRepository.findByEmployeeId(request.employeeId()).isPresent()) {
                throw new IllegalArgumentException("Employee ID already exists.");
            }

            user.setEmployeeId(request.employeeId());
            user.setUsername(request.username());
            user.setRole(request.role());
            user.setPermissions(request.permissions());

            userRepository.save(user);
            log.info(
                    "User {} updated. Role: {}, Permissions: {}", 
                    user.getUsername(), 
                    user.getRole(), 
                    user.getPermissions()
            );
            return toResponse(user);
        });
    }

    @Transactional
    public List<UserResponseDTO> batchUpdatePermissions(BatchPermissionDTO request) {
        List<UserEntity> users = userRepository.findAllById(request.userIds());
        if (users.size() != request.userIds().size()) {
            throw new IllegalArgumentException("One or more user IDs were not found.");
        }

        for (UserEntity user : users) {
            if (request.mode() == BatchPermissionDTO.Mode.OVERWRITE) {
                user.setPermissions(request.permissions());
            } else {
                // UPDATE mode: merge — add new permissions, remove explicitly unchecked ones
                var merged = new java.util.HashSet<>(user.getPermissions());
                merged.addAll(request.permissions());
                user.setPermissions(merged);
            }
            userRepository.save(user);
        }

        log.info("Batch permission {} applied to {} users", request.mode(), users.size());
        return users.stream().map(this::toResponse).toList();
    }

    private UserResponseDTO toResponse(UserEntity u) {
        return new UserResponseDTO(
                u.getId(), 
                u.getEmployeeId(), 
                u.getUsername(), 
                u.getRole(), 
                u.getPermissions(), 
                u.isActive()
        );
    }
}
