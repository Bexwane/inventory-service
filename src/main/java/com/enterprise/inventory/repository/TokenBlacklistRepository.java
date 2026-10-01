package com.enterprise.inventory.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import com.enterprise.inventory.entity.TokenBlacklistJpaEntity;

import java.time.Instant;
import java.util.UUID;
import java.util.List;

@Repository
public interface TokenBlacklistRepository extends JpaRepository<TokenBlacklistJpaEntity, UUID> {
    
    @Query("SELECT t FROM TokenBlacklistJpaEntity t WHERE t.expiresAt > :now")
    List<TokenBlacklistJpaEntity> findAllActive(@Param("now") Instant now);

    @Modifying
    @Query("DELETE FROM TokenBlacklistJpaEntity t WHERE t.expiresAt < :now")
    int deleteExpired(@Param("now") Instant now);
}
