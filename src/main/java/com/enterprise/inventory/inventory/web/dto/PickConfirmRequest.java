// ─────────────────────────────────────────────────────────────
// FILE: PickConfirmRequest.java
// New — step 2 of picking: worker scans confirmation after physical pick
// ─────────────────────────────────────────────────────────────
package com.enterprise.inventory.inventory.web.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record PickConfirmRequest(
        @NotBlank(message = "SKU is required")
        String sku,

        @NotNull(message = "Source location is required")
        UUID locationId,

        // How many were originally reserved for this task
        @Min(value = 1, message = "Reserved quantity must be at least 1")
        int reservedQty,

        // How many the worker actually picked — may be less (short pick)
        @Min(value = 0, message = "Actual quantity cannot be negative")
        int actualQty,

        @NotBlank(message = "Task ID is required")
        String taskId,

        // Flags — captured in movement log and used to fire alerts
        boolean flagShortPick,
        boolean flagDamageFound
) {}


