package com.enterprise.inventory.controller;

import com.enterprise.inventory.service.UserService;
import com.enterprise.inventory.dto.BatchPermissionDTO;
import com.enterprise.inventory.dto.CreateUserDTO;
import com.enterprise.inventory.dto.UpdateUserDTO;
import com.enterprise.inventory.dto.UserResponseDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for user administration tasks such as listing, creating, and modifying users.
 */
@Slf4j
@RestController
@RequestMapping("${app.api.base-path}${app.api.users.base}")
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

    @PatchMapping("${app.api.users.batch-permissions}")
    public ResponseEntity<List<UserResponseDTO>> batchUpdatePermissions(
            @Valid @RequestBody BatchPermissionDTO request) {
        return ResponseEntity.ok(userService.batchUpdatePermissions(request));
    }
}
