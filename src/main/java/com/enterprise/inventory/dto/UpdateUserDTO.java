package com.enterprise.inventory.dto;

import com.enterprise.inventory.entity.Permission;
import com.enterprise.inventory.entity.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.Set;

/**
 * Data transfer object representing a request to update a user's details.
 */
public record UpdateUserDTO(
        @NotBlank(message = "Employee ID is required")
        String employeeId,

        @NotBlank(message = "Username is required")
        String username,

        @NotNull(message = "Role is required")
        Role role,

        @NotNull(message = "Permissions cannot be null")
        Set<Permission> permissions
) {}
