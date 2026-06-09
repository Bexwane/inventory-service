// ─────────────────────────────────────────────────────────────
// FILE: ReleaseReservationRequest.java
// New — release a reservation when a task is cancelled or times out
// ─────────────────────────────────────────────────────────────
package com.enterprise.inventory.inventory.web.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record ReleaseReservationRequest(
        @NotBlank(message = "SKU is required")
        String sku,

        @NotNull(message = "Location is required")
        UUID locationId,

        @Min(value = 1, message = "Quantity must be at least 1")
        int qty,

        @NotBlank(message = "Task ID is required")
        String taskId
) {}


