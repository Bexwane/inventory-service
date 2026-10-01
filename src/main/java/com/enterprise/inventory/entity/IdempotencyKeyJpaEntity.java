package com.enterprise.inventory.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Column;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * PostgreSQL persistent entity for Idempotency Keys.
 * Acts as the System of Record to prevent double-processing of requests
 * if the Redis cache is offline.
 */
@Entity
@Table(name = "idempotency_keys")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IdempotencyKeyJpaEntity {

    @Id
    @Column(name = "key", nullable = false, updatable = false)
    private String key;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "response_payload", columnDefinition = "jsonb")
    private String responsePayload;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
}
