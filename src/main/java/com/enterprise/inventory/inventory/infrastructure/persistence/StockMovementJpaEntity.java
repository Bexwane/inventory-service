package com.enterprise.inventory.inventory.infrastructure.persistence;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Immutable;

import java.time.Instant;
import java.util.UUID;

/**
 * Append-only audit ledger — one row per stock movement.
 *
 * WHY THIS EXISTS:
 * Every inventory change (receive, pick, adjust) writes a row here
 * inside the same transaction. This gives you:
 *   - Complete audit trail for compliance
 *   - Ability to reconstruct stock level at any point in time
 *   - Source data for SAP goods movements (synced_to_sap flag)
 *   - Evidence for discrepancy investigations
 *
 * @Immutable prevents Hibernate from ever issuing an UPDATE on this table.
 * Rows are written once and never changed — this is the legal record.
 *
 * The synced_to_sap flag is polled by the Kafka publisher:
 * unsync'd rows are published to wms.stock.movement and the SAP adapter
 * sets this flag after a successful goods posting.
 */
@Entity
@Immutable                              // FIX: Hibernate will never UPDATE this table
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
        RECEIVE,        // stock came in via putaway
        PICK,           // stock left via picking
        ADJUST_UP,      // manual positive adjustment (supervisor only)
        ADJUST_DOWN,    // manual negative adjustment (supervisor only)
        RESERVE,        // qty reserved for a pick task
        RELEASE         // reservation released (task cancelled/timed out)
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

    // The task or order that caused this movement
    @Column(name = "reference_id", length = 100)
    private String referenceId;

    @Column(name = "reference_type", length = 50)
    private String referenceType;   // "PUTAWAY_TASK", "PICK_TASK", "ADJUSTMENT"

    @Column(name = "performed_by", nullable = false)
    private UUID performedBy;

    // FIX: set automatically — cannot be faked by application code
    @CreationTimestamp
    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    // FIX: SAP sync flag — Kafka publisher queries WHERE synced_to_sap = false
    @Column(name = "synced_to_sap", nullable = false)
    private boolean syncedToSap = false;

    // Factory method — use this instead of a constructor to make intent clear
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