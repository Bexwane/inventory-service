// ─────────────────────────────────────────────────────────────
// FILE: src/main/java/com/enterprise/inventory/inventory/web/dto/InventoryResponse.java
// ─────────────────────────────────────────────────────────────
package com.enterprise.inventory.inventory.web.dto;

import java.util.UUID;

/**
 * FIX: now includes qtyReserved, qtyAvailable, and locationId.
 * The original only returned sku + availableQuantity + version.
 * A scanner needs to know where the stock is and how much is free.
 */
public record InventoryResponse(
        String sku,
        int qtyOnHand,
        int qtyReserved,
        int qtyAvailable,   // computed: onHand - reserved
        UUID locationId,
        Long version
) {}