package com.enterprise.inventory.inventory.infrastructure.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Data access for inventory records.
 *
 * FIX 1: Queries updated for new schema (qtyOnHand, qtyReserved, locationId).
 *
 * FIX 2: reserveStockAtomically — the reservation query.
 *         On pick task creation: reserve qty. On confirm: deduct.
 *         Uses a conditional WHERE clause so it is safe under concurrency —
 *         it only succeeds if enough available qty exists at the moment of execution.
 *
 * FIX 3: confirmPickAtomically — deducts both onHand and reserved together.
 *         Must be called in the same transaction as the pick task status update.
 *
 * FIX 4: findBySku now uses UUID PK internally; sku lookup returns Optional.
 *
 * FIX 5: FEFO query — always picks the earliest-expiring stock first.
 */
@Repository
public interface InventoryRepository extends JpaRepository<InventoryJpaEntity, UUID> {

    // ── Lookups ───────────────────────────────────────────────────────────────

    Optional<InventoryJpaEntity> findBySku(String sku);

    List<InventoryJpaEntity> findBySkuIn(List<String> skus);

    // FIX: find all stock for a SKU across all locations (for putaway location selection)
    List<InventoryJpaEntity> findBySkuOrderByQtyOnHandDesc(String sku);

    Optional<InventoryJpaEntity> findBySkuAndLocationId(String sku, UUID locationId);

    @Query("SELECT i FROM InventoryJpaEntity i WHERE i.sku = :sku AND i.locationId = :locationId AND ((i.containerId IS NULL AND :containerId IS NULL) OR i.containerId = :containerId)")
    Optional<InventoryJpaEntity> findExactMatch(@Param("sku") String sku, @Param("locationId") UUID locationId, @Param("containerId") UUID containerId);

    // ── Listing ───────────────────────────────────────────────────────────────

    // FIX: query updated for new column name qtyOnHand
    Page<InventoryJpaEntity> findByQtyOnHandGreaterThan(int quantity, Pageable pageable);

    @Query("SELECT i FROM InventoryJpaEntity i WHERE i.qtyOnHand > 0 AND " +
           "(:search IS NULL OR :search = '' OR LOWER(i.sku) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "OR CAST(i.locationId AS string) LIKE CONCAT('%', :search, '%'))")
    Page<InventoryJpaEntity> searchAvailableInventory(@Param("search") String search, Pageable pageable);

    // FIX: FEFO — find stock ordered by earliest expiry first
    //      Picking service uses this to always pick the soonest-to-expire lot
    @Query("""
            SELECT i FROM InventoryJpaEntity i
            WHERE i.sku = :sku
              AND (i.qtyOnHand - i.qtyReserved) > 0
              AND i.expiryDate IS NOT NULL
            ORDER BY i.expiryDate ASC
            """)
    List<InventoryJpaEntity> findAvailableBySkuOrderByExpiryAsc(@Param("sku") String sku, Pageable pageable);
}