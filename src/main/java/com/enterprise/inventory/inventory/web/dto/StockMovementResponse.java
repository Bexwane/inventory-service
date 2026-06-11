package com.enterprise.inventory.inventory.web.dto;

import java.time.Instant;
import java.util.UUID;

public record StockMovementResponse(
        UUID id,
        String movementType,
        String sku,
        UUID fromLocationId,
        UUID toLocationId,
        UUID containerId,
        int qty,
        String referenceId,
        String referenceType,
        Instant occurredAt,
        boolean syncedToSap
) {}
