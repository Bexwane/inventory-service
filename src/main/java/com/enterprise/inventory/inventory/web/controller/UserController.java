package com.enterprise.inventory.inventory.web.controller;

import com.enterprise.inventory.inventory.infrastructure.persistence.UserEntity;
import com.enterprise.inventory.inventory.infrastructure.persistence.UserRepository;
import com.enterprise.inventory.inventory.web.dto.CreateUserRequest;
import com.enterprise.inventory.inventory.web.dto.UpdateUserRequest;
import com.enterprise.inventory.inventory.web.dto.UserResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * User management — MANAGER only.
 * Allows admins to create workers, deactivate them, and view the full list.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/users")
@PreAuthorize("hasAuthority('CAN_MANAGE_USERS')")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository  userRepository;
    private final PasswordEncoder passwordEncoder;

    /** List all users — active and inactive. */
    @GetMapping
    public ResponseEntity<List<UserResponse>> listUsers() {
        List<UserResponse> users = userRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
        return ResponseEntity.ok(users);
    }

    /** Create a new warehouse worker. */
    @PostMapping
    public ResponseEntity<UserResponse> createUser(@Valid @RequestBody CreateUserRequest request) {
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
        log.info("New user created: {} ({}) with {} permissions", request.username(), request.role(), request.permissions().size());

        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(user));
    }

    /** Deactivate a user — they can no longer log in. Does NOT delete. */
    @PatchMapping("/{id}/deactivate")
    public ResponseEntity<UserResponse> deactivateUser(@PathVariable UUID id) {
        return userRepository.findById(id).map(user -> {
            user.setActive(false);
            userRepository.save(user);
            log.info("User deactivated: {}", user.getUsername());
            return ResponseEntity.ok(toResponse(user));
        }).orElse(ResponseEntity.notFound().build());
    }

    /** Reactivate a previously deactivated user. */
    @PatchMapping("/{id}/activate")
    public ResponseEntity<UserResponse> activateUser(@PathVariable UUID id) {
        return userRepository.findById(id).map(user -> {
            user.setActive(true);
            userRepository.save(user);
            log.info("User activated: {}", user.getUsername());
            return ResponseEntity.ok(toResponse(user));
        }).orElse(ResponseEntity.notFound().build());
    }

    /** Edit a user's role and custom permissions. */
    @PutMapping("/{id}")
    public ResponseEntity<UserResponse> updateUser(@PathVariable UUID id, @Valid @RequestBody UpdateUserRequest request) {
        return userRepository.findById(id).map(user -> {
            if (!user.getUsername().equals(request.username()) && userRepository.findByUsername(request.username()).isPresent()) {
                throw new IllegalArgumentException("Username already exists.");
            }
            if (!user.getEmployeeId().equals(request.employeeId()) && userRepository.findByEmployeeId(request.employeeId()).isPresent()) {
                throw new IllegalArgumentException("Employee ID already exists.");
            }

            user.setEmployeeId(request.employeeId());
            user.setUsername(request.username());
            user.setRole(request.role());
            user.setPermissions(request.permissions());
            
            userRepository.save(user);
            log.info("User {} updated. Role: {}, Permissions: {}", user.getUsername(), user.getRole(), user.getPermissions());
            return ResponseEntity.ok(toResponse(user));
        }).orElse(ResponseEntity.notFound().build());
    }

    private UserResponse toResponse(UserEntity u) {
        return new UserResponse(u.getId(), u.getEmployeeId(), u.getUsername(), u.getRole(), u.getPermissions(), u.isActive());
    }
}
