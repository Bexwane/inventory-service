// ─────────────────────────────────────────────────────────────
// FILE: PickReserveRequest.java
// New — step 1 of picking: reserve qty before dispatching the worker
// ─────────────────────────────────────────────────────────────
package com.enterprise.inventory.inventory.web.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record PickReserveRequest(
        @NotBlank(message = "SKU is required")
        String sku,

        @NotNull(message = "Source location is required")
        UUID locationId,

        @Min(value = 1, message = "Quantity must be at least 1")
        int qty,

        @NotBlank(message = "Task ID is required")
        String taskId
) {}


