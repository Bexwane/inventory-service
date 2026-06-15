package com.enterprise.inventory.inventory.infrastructure.persistence;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Immutable;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity representing a stock movement audit ledger entry.
 */
@Entity
@Immutable
@Table(
        name = "stock_movements",
        indexes = {
                @Index(name = "idx_movement_sku",      columnList = "sku"),
                @Index(name = "idx_movement_ref",      columnList = "reference_id, reference_type"),
                @Index(name = "idx_movement_sap_sync", columnList = "synced_to_sap, occurred_at")
        }
)
@Getter
@NoArgsConstructor
public class StockMovementJpaEntity {

    public enum MovementType {
        RECEIVE,
        PICK,
        ADJUST_UP,
        ADJUST_DOWN,
        RESERVE,
        RELEASE
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "movement_type", nullable = false, length = 20)
    private MovementType movementType;

    @Column(name = "sku", nullable = false, length = 100)
    private String sku;

    @Column(name = "from_location_id")
    private UUID fromLocationId;

    @Column(name = "to_location_id")
    private UUID toLocationId;

    @Column(name = "container_id")
    private UUID containerId;

    @Column(name = "qty", nullable = false)
    private int qty;

    @Column(name = "reference_id", length = 100)
    private String referenceId;

    @Column(name = "reference_type", length = 50)
    private String referenceType;

    @Column(name = "performed_by", nullable = false)
    private UUID performedBy;

    @CreationTimestamp
    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(name = "synced_to_sap", nullable = false)
    private boolean syncedToSap = false;

    public static StockMovementJpaEntity of(MovementType type, String sku,
                                            UUID fromLocation, UUID toLocation,
                                            UUID container, int qty,
                                            String referenceId, String referenceType,
                                            UUID performedBy) {
        StockMovementJpaEntity e = new StockMovementJpaEntity();
        e.movementType  = type;
        e.sku           = sku;
        e.fromLocationId = fromLocation;
        e.toLocationId  = toLocation;
        e.containerId   = container;
        e.qty           = qty;
        e.referenceId   = referenceId;
        e.referenceType = referenceType;
        e.performedBy   = performedBy;
        e.syncedToSap   = false;
        return e;
    }
}