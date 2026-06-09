package com.enterprise.inventory.inventory.infrastructure.persistence;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Database entity for a single inventory record.
 * One row = one SKU at one location in one container.
 *
 * FIX 1: SKU is no longer the primary key.
 *         SKU codes can be renamed, merged, or reassigned in enterprise systems.
 *         A surrogate UUID PK is stable forever. SKU gets a unique index instead.
 *
 * FIX 2: locationId and containerId added.
 *         The original entity could not answer "where is this stock?"
 *         which is the entire point of a WMS.
 *
 * FIX 3: qtyReserved added.
 *         Alongside availableQuantity (on_hand) this enables the
 *         reservation pattern: reserve on task creation, deduct on confirm.
 *         Available = on_hand - reserved. Two pickers can never get the same stock.
 *
 * FIX 4: createdAt and updatedAt audit timestamps added.
 *         Required for compliance — "when was this record last changed?"
 *
 * FIX 5: @Column constraints added — nullable=false, precision on qty checks.
 *         Without these, the DB accepts null quantities which breaks all arithmetic.
 */
@Entity
@Table(
        name = "inventory_items",
        indexes = {
                // FIX: composite index for the most common query: "find stock at this location"
                @Index(name = "idx_inventory_location", columnList = "location_id, container_id"),
                // FIX: non-unique index on SKU for fast lookups
                @Index(name = "idx_inventory_sku", columnList = "sku")
        }
)
@Getter
@Setter
public class InventoryJpaEntity {

    // FIX: UUID primary key — stable even if SKU codes change
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // FIX: non-unique index but NOT the PK — can be updated without cascade issues
    @Column(name = "sku", nullable = false, length = 100)
    private String sku;

    // FIX: where the stock physically is — without this the entity is useless in a WMS
    @Column(name = "location_id", nullable = false)
    private UUID locationId;

    // FIX: which container (pallet, tote, bin) holds this stock
    @Column(name = "container_id")
    private UUID containerId;

    // FIX: renamed for clarity — this is physical stock, not "available"
    @Column(name = "qty_on_hand", nullable = false)
    private int qtyOnHand;

    // FIX: qty committed to a pick order but not yet physically moved
    //      availableQty = qtyOnHand - qtyReserved
    //      This prevents two pickers being sent to the same bin
    @Column(name = "qty_reserved", nullable = false)
    private int qtyReserved = 0;

    // Computed — not stored, derived on read for convenience
    @Transient
    public int getQtyAvailable() {
        return qtyOnHand - qtyReserved;
    }

    // FIX: lot number for FEFO (First Expired First Out) — food, pharma, chemicals
    @Column(name = "lot_number", length = 50)
    private String lotNumber;

    @Column(name = "expiry_date")
    private Instant expiryDate;

    // FIX: audit timestamps — compliance requires knowing when a row last changed
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // FIX: optimistic lock version — prevents lost updates under concurrent access
    @Version
    private Long version;

    // ── Domain operations ─────────────────────────────────────────────────────

    /** Putaway confirm — stock arrives in the bin. */
    public void receiveStock(int amount) {
        if (amount <= 0) throw new IllegalArgumentException("Receive amount must be positive");
        this.qtyOnHand += amount;
    }

    /** Pick task creation — reserve qty so no other picker is routed here. */
    public void reserveStock(int amount) {
        if (amount <= 0) throw new IllegalArgumentException("Reserve amount must be positive");
        if (amount > getQtyAvailable()) {
            throw new com.enterprise.inventory.inventory.application.InsufficientStockException(
                    "Cannot reserve " + amount + " of SKU " + sku +
                            ". Available: " + getQtyAvailable());
        }
        this.qtyReserved += amount;
    }

    /**
     * Pick confirmation — worker scanned the items physically.
     * actualAmount may be less than reservedAmount (short pick).
     */
    public void confirmPick(int reservedAmount, int actualAmount) {
        if (actualAmount < 0)         throw new IllegalArgumentException("Actual amount cannot be negative");
        if (actualAmount > reservedAmount) throw new IllegalArgumentException(
                "Actual amount cannot exceed reserved amount");
        if (reservedAmount > qtyReserved) throw new IllegalArgumentException(
                "Reserved amount " + reservedAmount + " exceeds current reservation " + qtyReserved);
        if (actualAmount > qtyOnHand) throw new IllegalArgumentException(
                "Actual amount " + actualAmount + " exceeds onHand qty " + qtyOnHand);

        this.qtyOnHand   -= actualAmount;
        this.qtyReserved -= reservedAmount;
    }

    /** Release reservation without deducting — task cancelled or timed out. */
    public void releaseReservation(int amount) {
        if (amount > qtyReserved) throw new IllegalArgumentException(
                "Cannot release " + amount + " — only " + qtyReserved + " reserved");
        this.qtyReserved -= amount;
    }
}