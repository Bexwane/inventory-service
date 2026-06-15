package com.enterprise.inventory.inventory.web.controller;

import com.enterprise.inventory.inventory.application.UserService;
import com.enterprise.inventory.inventory.web.dto.BatchPermissionDTO;
import com.enterprise.inventory.inventory.web.dto.CreateUserDTO;
import com.enterprise.inventory.inventory.web.dto.UpdateUserDTO;
import com.enterprise.inventory.inventory.web.dto.UserResponseDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for user administration tasks such as listing, creating, and modifying users.
 */
@RestController
@RequestMapping("/api/v1/users")
@PreAuthorize("hasAuthority('CAN_MANAGE_USERS')")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping
    public ResponseEntity<List<UserResponseDTO>> listUsers() {
        return ResponseEntity.ok(userService.listUsers());
    }

    @PostMapping
    public ResponseEntity<UserResponseDTO> createUser(@Valid @RequestBody CreateUserDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.createUser(request));
    }

    @PatchMapping("/{id}/deactivate")
    public ResponseEntity<UserResponseDTO> deactivateUser(@PathVariable UUID id) {
        return userService.deactivateUser(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PatchMapping("/{id}/activate")
    public ResponseEntity<UserResponseDTO> activateUser(@PathVariable UUID id) {
        return userService.activateUser(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<UserResponseDTO> updateUser(@PathVariable UUID id,
                                                   @Valid @RequestBody UpdateUserDTO request) {
        return userService.updateUser(id, request)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PatchMapping("/batch/permissions")
    public ResponseEntity<List<UserResponseDTO>> batchUpdatePermissions(
            @Valid @RequestBody BatchPermissionDTO request) {
        return ResponseEntity.ok(userService.batchUpdatePermissions(request));
    }
}
