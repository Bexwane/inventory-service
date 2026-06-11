package com.enterprise.inventory.inventory.web.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Step 2 of the picking flow: worker physically scans items from the bin.
 *
 * actualQty may be LESS than reservedQty (short pick) — e.g. a box was damaged.
 * The system detects this automatically by comparing the two values.
 */
public record PickConfirmRequest(
        @NotBlank(message = "SKU is required")
        String sku,

        @NotNull(message = "Source location is required")
        UUID locationId,

        UUID containerId,

        // How many were originally reserved for this task
        @Min(value = 1, message = "Reserved quantity must be at least 1")
        int reservedQty,

        // How many the worker actually picked — may be less (short pick)
        @Min(value = 0, message = "Actual quantity cannot be negative")
        int actualQty,

        @NotBlank(message = "Task ID is required for audit trail")
        String taskId
) {}
