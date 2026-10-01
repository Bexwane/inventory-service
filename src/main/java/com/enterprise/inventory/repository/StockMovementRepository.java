package com.enterprise.inventory.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import com.enterprise.inventory.entity.StockMovementJpaEntity;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Repository interface for managing data access operations on stock movements.
 */
@Repository
public interface StockMovementRepository extends JpaRepository<StockMovementJpaEntity, UUID> {

    Page<StockMovementJpaEntity> findByPerformedByOrderByOccurredAtDesc(UUID performedBy, Pageable pageable);

    @Query(value = """
            SELECT * FROM stock_movements
            WHERE synced_to_sap = false
              AND occurred_at < :cutoff
            ORDER BY occurred_at ASC
            LIMIT :limit
            """, nativeQuery = true)
    List<StockMovementJpaEntity> findUnsyncedMovementsOlderThan(
            @Param("cutoff") Instant cutoff,
            @Param("limit") int limit);

    @Modifying(clearAutomatically = true)
    @Query(value = "UPDATE stock_movements SET synced_to_sap = true WHERE id = :id",
           nativeQuery = true)
    void markSyncedToSap(@Param("id") UUID id);
}

