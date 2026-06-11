package com.enterprise.inventory.inventory.web.dto;

import com.enterprise.inventory.inventory.infrastructure.persistence.Permission;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.Set;

public record UpdateUserRequest(
        @NotBlank(message = "Employee ID is required")
        String employeeId,

        @NotBlank(message = "Username is required")
        String username,

        @NotBlank(message = "Role is required")
        @Pattern(regexp = "WORKER|SUPERVISOR|MANAGER|PICKER_ONLY", message = "Role is invalid")
        String role,

        @NotNull(message = "Permissions cannot be null")
        Set<Permission> permissions
) {}
