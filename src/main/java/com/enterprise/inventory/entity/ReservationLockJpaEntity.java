package com.enterprise.inventory.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import jakarta.persistence.Column;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;


/**
 * PostgreSQL persistent entity for Reservation Locks.
 * Acts as the System of Record to prevent reservation theft loopholes 
 * during Redis crashes or amnesia.
 */
@Entity
@Table(name = "reservation_locks")
@IdClass(ReservationLockId.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReservationLockJpaEntity {

    @Id
    @Column(name = "task_id", nullable = false)
    private String taskId;

    @Id
    @Column(name = "sku", nullable = false)
    private String sku;

    @Id
    @Column(name = "location_id", nullable = false)
    private UUID locationId;

    @Column(name = "container_id")
    private UUID containerId;

    @Column(name = "qty", nullable = false)
    private Integer qty;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
}
