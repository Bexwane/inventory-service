package com.enterprise.inventory.messaging;

import java.util.UUID;

/**
 * Spring application event representing inventory changes, processed after database transaction commits.
 */
public record InventoryEvent(
        Type     eventType,
        UUID     movementId,
        String   sku,
        UUID     locationId,
        UUID     containerId,
        int      qty,
        String   taskId,
        int      discrepancy
) {
    public enum Type {
        STOCK_RECEIVED,
        PICK_CONFIRMED,
        SHORT_PICK
    }
}
