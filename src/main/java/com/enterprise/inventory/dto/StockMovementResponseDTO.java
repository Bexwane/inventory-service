package com.enterprise.inventory.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Data transfer object representing the response with stock movement details.
 */
public record StockMovementResponseDTO(
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
