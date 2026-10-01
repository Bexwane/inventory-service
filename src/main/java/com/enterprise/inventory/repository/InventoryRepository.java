package com.enterprise.inventory.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import com.enterprise.inventory.entity.InventoryJpaEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for managing data access operations on inventory items.
 * Only methods actively used by InventoryService are declared here.
 */
@Repository
public interface InventoryRepository extends JpaRepository<InventoryJpaEntity, UUID> {

    Optional<InventoryJpaEntity> findBySku(String sku);

    List<InventoryJpaEntity> findBySkuIn(List<String> skus);

    List<InventoryJpaEntity> findBySkuOrderByQtyOnHandDesc(String sku);

    Optional<InventoryJpaEntity> findBySkuAndLocationId(String sku, UUID locationId);

    /**
     * Exact three-part lookup: sku + location + (container or null).
     * Used by receiveStock, reserveStock, confirmPick, and releaseReservation.
     */
    @Query("SELECT i FROM InventoryJpaEntity i WHERE i.sku = :sku AND i.locationId = :locationId AND ((i.containerId IS NULL AND :containerId IS NULL) OR i.containerId = :containerId)")
    Optional<InventoryJpaEntity> findExactMatch(@Param("sku") String sku,
                                                @Param("locationId") UUID locationId,
                                                @Param("containerId") UUID containerId);

    /** Used by InventoryService.getInventory() when no search term is provided. */
    Page<InventoryJpaEntity> findByQtyOnHandGreaterThan(int quantity, Pageable pageable);

    /**
     * Full-text search across SKU and location for the Dashboard inventory view.
     */
    @Query("SELECT i FROM InventoryJpaEntity i WHERE i.qtyOnHand > 0 AND " +
           "(:search IS NULL OR :search = '' OR LOWER(i.sku) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "OR CAST(i.locationId AS string) LIKE CONCAT('%', :search, '%'))")
    Page<InventoryJpaEntity> searchAvailableInventory(@Param("search") String search, Pageable pageable);

    @Query("""
            SELECT i FROM InventoryJpaEntity i
            WHERE i.sku = :sku
              AND (i.qtyOnHand - i.qtyReserved) > 0
              AND i.expiryDate IS NOT NULL
            ORDER BY i.expiryDate ASC
            """)
    List<InventoryJpaEntity> findAvailableBySkuOrderByExpiryAsc(@Param("sku") String sku, Pageable pageable);
}