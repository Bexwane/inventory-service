package com.enterprise.inventory.inventory.web.dto;

import java.util.UUID;

/**
 * Data transfer object representing the response for an inventory item.
 */
public record InventoryResponseDTO(
        String sku,
        int qtyOnHand,
        int qtyReserved,
        int qtyAvailable,
        UUID locationId,
        UUID containerId,
        Long version
) {}