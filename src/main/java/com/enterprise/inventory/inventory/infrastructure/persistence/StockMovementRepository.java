package com.enterprise.inventory.inventory.infrastructure.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface StockMovementRepository extends JpaRepository<StockMovementJpaEntity, UUID> {
    
    Page<StockMovementJpaEntity> findByPerformedByOrderByOccurredAtDesc(UUID performedBy, Pageable pageable);
}
