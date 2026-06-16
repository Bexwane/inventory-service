package com.enterprise.inventory.inventory.infrastructure.persistence;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity representing an inventory record of a SKU at a physical location.
 */
@Entity
@Table(
        name = "inventory_items",
        indexes = {
                @Index(name = "idx_inventory_location", columnList = "location_id, container_id"),
                @Index(name = "idx_inventory_sku", columnList = "sku")
        }
)
@Getter
@Setter
public class InventoryJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "sku", nullable = false, length = 100)
    private String sku;

    @Column(name = "location_id", nullable = false)
    private UUID locationId;

    @Column(name = "container_id")
    private UUID containerId;

    @Column(name = "qty_on_hand", nullable = false)
    private int qtyOnHand;

    @Column(name = "qty_reserved", nullable = false)
    private int qtyReserved = 0;

    @Transient
    public int getQtyAvailable() {
        return qtyOnHand - qtyReserved;
    }

    @Column(name = "lot_number", length = 50)
    private String lotNumber;

    @Column(name = "expiry_date")
    private Instant expiryDate;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    public void receiveStock(int amount) {
        if (amount <= 0) throw new IllegalArgumentException("Receive amount must be positive");
        this.qtyOnHand += amount;
    }

    public void reserveStock(int amount) {
        if (amount <= 0) throw new IllegalArgumentException("Reserve amount must be positive");
        if (amount > getQtyAvailable()) {
            throw new com.enterprise.inventory.inventory.application.InsufficientStockException(
                    "Cannot reserve " + amount + " of SKU " + sku +
                            ". Available: " + getQtyAvailable());
        }
        this.qtyReserved += amount;
    }

    public void confirmPick(int reservedAmount, int actualAmount) {
        if (actualAmount < 0) {
            throw new IllegalArgumentException("Actual amount cannot be negative");
        }
        if (actualAmount > reservedAmount) {
            throw new IllegalArgumentException("Actual amount cannot exceed reserved amount");
        }
        if (reservedAmount > qtyReserved) {
            throw new IllegalArgumentException("Reserved amount " + reservedAmount + " exceeds current reservation " + qtyReserved);
        }
        if (actualAmount > qtyOnHand) {
            throw new IllegalArgumentException("Actual amount " + actualAmount + " exceeds onHand qty " + qtyOnHand);
        }

        this.qtyOnHand   -= actualAmount;
        this.qtyReserved -= reservedAmount;
    }

    public void releaseReservation(int amount) {
        if (amount > qtyReserved) throw new IllegalArgumentException(
                "Cannot release " + amount + " — only " + qtyReserved + " reserved");
        this.qtyReserved -= amount;
    }
}