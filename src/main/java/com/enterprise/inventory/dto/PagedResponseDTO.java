package com.enterprise.inventory.dto;

import org.springframework.data.domain.Page;
import java.util.List;

/**
 * Data transfer object wrapping paginated lists to avoid leaking internal framework metadata.
 */
public record PagedResponseDTO<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean last
) {
    public static <T> PagedResponseDTO<T> from(Page<T> page) {
        return new PagedResponseDTO<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isLast()
        );
    }
}


