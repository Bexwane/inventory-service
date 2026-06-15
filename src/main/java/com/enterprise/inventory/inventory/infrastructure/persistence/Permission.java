package com.enterprise.inventory.inventory.infrastructure.persistence;

/**
 * Enum defining the custom granular privileges available for warehouse users.
 */
public enum Permission {
    CAN_PICK,
    CAN_PUTAWAY,
    CAN_MANAGE_INVENTORY,
    CAN_MANAGE_USERS
}
