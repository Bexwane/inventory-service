package com.enterprise.inventory.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Data transfer object representing a specific item in a batch putaway request.
 */
public record ItemPutawayDTO(
        @NotBlank(message = "SKU is required")
        String sku,

        @NotNull(message = "Destination location is required")
        UUID destinationLocationId,

        @Min(value = 1, message = "Quantity must be at least 1")
        int qty
) {}
