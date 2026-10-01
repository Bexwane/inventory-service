package com.enterprise.inventory.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Column;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * PostgreSQL persistent entity for the JWT Token Blacklist.
 * Acts as the System of Record to prevent token revocation loopholes 
 * during Redis crashes or amnesia.
 */
@Entity
@Table(name = "token_blacklist")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TokenBlacklistJpaEntity {

    @Id
    @Column(name = "jti", nullable = false, updatable = false)
    private UUID jti;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
}
