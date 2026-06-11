package com.enterprise.inventory.inventory.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record BatchPutawayRequest(
        @NotBlank(message = "Task ID is required")
        String taskId,

        @NotNull(message = "Source location ID is required")
        UUID sourceLocationId,

        @NotEmpty(message = "Must include at least one container")
        @Valid
        List<ContainerPutawayDTO> containers
) {}
