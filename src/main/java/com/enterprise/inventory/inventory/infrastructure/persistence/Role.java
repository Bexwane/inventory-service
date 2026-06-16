package com.enterprise.inventory.inventory.infrastructure.persistence;

/**
 * Defines the roles available within the inventory system.
 */
public enum Role {
    WORKER,
    SUPERVISOR,
    MANAGER,
    PICKER_ONLY,
    VIEWER_ONLY,
    SUSPENDED
}
