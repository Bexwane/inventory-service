package com.enterprise.inventory.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import com.enterprise.inventory.entity.IdempotencyKeyJpaEntity;

import java.time.Instant;

@Repository
public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKeyJpaEntity, String> {

    @Modifying
    @Query("DELETE FROM IdempotencyKeyJpaEntity i WHERE i.expiresAt < :now")
    int deleteExpired(@Param("now") Instant now);
}
