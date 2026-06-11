// ─────────────────────────────────────────────────────────────
// FILE: ReceiveStockRequest.java
// FIX: includes locationId and taskId (original AdjustStockRequest had neither)
// ─────────────────────────────────────────────────────────────
package com.enterprise.inventory.inventory.web.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record ReceiveStockRequest(
        @NotBlank(message = "SKU is required")
        String sku,

        @NotNull(message = "Destination location is required")
        UUID locationId,

        UUID containerId,

        @Min(value = 1, message = "Quantity must be at least 1")
        int qty,

        @NotBlank(message = "Task ID is required for audit trail")
        String taskId
) {}


