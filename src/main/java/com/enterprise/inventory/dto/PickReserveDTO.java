package com.enterprise.inventory.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Data transfer object representing a request to reserve stock for picking.
 */
public record PickReserveDTO(
        @NotBlank(message = "SKU is required")
        String sku,

        @NotNull(message = "Source location is required")
        UUID locationId,

        UUID containerId,

        @Min(value = 1, message = "Quantity must be at least 1")
        int qty,

        @NotBlank(message = "Task ID is required for audit trail")
        String taskId
) {}
