package com.enterprise.inventory.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import com.enterprise.inventory.entity.ReservationLockJpaEntity;
import com.enterprise.inventory.entity.ReservationLockId;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReservationLockRepository extends JpaRepository<ReservationLockJpaEntity, ReservationLockId> {

    @Query("SELECT r FROM ReservationLockJpaEntity r WHERE r.taskId = :taskId AND r.sku = :sku AND r.locationId = :locationId")
    Optional<ReservationLockJpaEntity> findLock(
            @Param("taskId") String taskId, 
            @Param("sku") String sku,
            @Param("locationId") UUID locationId
    );

    @Modifying
    @Query("DELETE FROM ReservationLockJpaEntity r WHERE r.taskId = :taskId AND r.sku = :sku AND r.locationId = :locationId")
    int deleteLock(
            @Param("taskId") String taskId,
            @Param("sku") String sku,
            @Param("locationId") UUID locationId
    );

    @Query("SELECT r FROM ReservationLockJpaEntity r WHERE r.expiresAt > :now")
    List<ReservationLockJpaEntity> findAllActive(@Param("now") Instant now);

    @Modifying
    @Query("DELETE FROM ReservationLockJpaEntity r WHERE r.expiresAt < :now")
    int deleteExpired(@Param("now") Instant now);
}
