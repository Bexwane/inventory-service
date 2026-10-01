package com.enterprise.inventory.dto;

import com.enterprise.inventory.entity.Permission;
import com.enterprise.inventory.entity.Role;
import java.util.Set;
import java.util.UUID;

/**
 * Data transfer object representing the response with user details.
 */
public record UserResponseDTO(
        UUID id,
        String employeeId,
        String username,
        Role role,
        Set<Permission> permissions,
        boolean isActive
) {}
