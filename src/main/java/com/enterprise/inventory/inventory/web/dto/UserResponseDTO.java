package com.enterprise.inventory.inventory.web.dto;

import com.enterprise.inventory.inventory.infrastructure.persistence.Permission;
import com.enterprise.inventory.inventory.infrastructure.persistence.Role;
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
