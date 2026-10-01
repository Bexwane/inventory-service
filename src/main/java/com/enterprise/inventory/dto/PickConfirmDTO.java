package com.enterprise.inventory.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Data transfer object representing a request to confirm a picking task.
 */
public record PickConfirmDTO(
        @NotBlank(message = "SKU is required")
        String sku,

        @NotNull(message = "Source location is required")
        UUID locationId,

        UUID containerId,

        @Min(value = 1, message = "Reserved quantity must be at least 1")
        int reservedQty,

        @Min(value = 0, message = "Actual quantity cannot be negative")
        int actualQty,

        @NotBlank(message = "Task ID is required for audit trail")
        String taskId
) {}
