package com.enterprise.inventory.dto;

import com.enterprise.inventory.entity.Permission;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Data transfer object for atomically updating permissions on a batch of users in a single transaction.
 */
public record BatchPermissionDTO(

        @NotEmpty(message = "At least one user ID is required")
        List<UUID> userIds,

        @NotNull(message = "Permissions cannot be null")
        Set<Permission> permissions,

        /**
         * OVERWRITE: fully replaces each user's permissions with the provided set.
         * UPDATE: merges the provided set with existing permissions (adds new ones, removes unchecked ones).
         */
        @NotNull(message = "Mode is required")
        Mode mode
) {
    public enum Mode { OVERWRITE, UPDATE }
}
