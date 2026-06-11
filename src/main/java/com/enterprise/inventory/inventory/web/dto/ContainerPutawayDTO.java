package com.enterprise.inventory.inventory.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record ContainerPutawayDTO(
        @NotNull(message = "Container ID is required")
        UUID containerId,

        @NotEmpty(message = "Container must have at least one item")
        @Valid
        List<ItemPutawayDTO> items
) {}
