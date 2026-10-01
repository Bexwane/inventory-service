package com.enterprise.inventory.service;

import com.enterprise.inventory.entity.UserEntity;
import com.enterprise.inventory.repository.UserRepository;
import com.enterprise.inventory.dto.BatchPermissionDTO;
import com.enterprise.inventory.dto.CreateUserDTO;
import com.enterprise.inventory.dto.UpdateUserDTO;
import com.enterprise.inventory.dto.UserResponseDTO;
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

    /**
     * Provisions a new warehouse user.
     * Ensures that the username and Employee ID are globally unique before saving.
     *
     * @param request DTO containing the user's role, permissions, and raw password.
     * @return UserResponseDTO representing the newly saved user (password excluded).
     */
    @Transactional
    public UserResponseDTO createUser(CreateUserDTO request) {
        validateUniqueConstraints(request.username(), request.employeeId(), null);

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
            validateUniqueConstraints(request.username(), request.employeeId(), id);

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

    /**
     * Atomically updates permissions for multiple users at once.
     * Essential for managing large teams of warehouse workers efficiently.
     * 
     * @param request DTO containing a list of user IDs and the new permissions to apply.
     * @return List of updated UserResponseDTOs.
     */
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

    /**
     * Prevents database uniqueness constraint violations by eagerly checking 
     * if the requested username or Employee ID is already registered to another user.
     * 
     * @param username The requested username.
     * @param employeeId The requested employee ID (e.g., EMP-001).
     * @param excludeUserId Used during updates to allow a user to keep their current username/ID.
     */
    private void validateUniqueConstraints(String username, String employeeId, UUID excludeUserId) {
        userRepository.findByUsername(username).ifPresent(user -> {
            if (excludeUserId == null || !user.getId().equals(excludeUserId)) {
                throw new IllegalArgumentException("Username already exists.");
            }
        });

        userRepository.findByEmployeeId(employeeId).ifPresent(user -> {
            if (excludeUserId == null || !user.getId().equals(excludeUserId)) {
                throw new IllegalArgumentException("Employee ID already exists.");
            }
        });
    }
}
