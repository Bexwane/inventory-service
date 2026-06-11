package com.enterprise.inventory.inventory.web.dto;

import com.enterprise.inventory.inventory.infrastructure.persistence.Permission;
import java.util.Set;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String employeeId,
        String username,
        String role,
        Set<Permission> permissions,
        boolean isActive
) {}
