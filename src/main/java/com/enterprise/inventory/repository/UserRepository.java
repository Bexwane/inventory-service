package com.enterprise.inventory.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.enterprise.inventory.entity.UserEntity;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for managing data access operations on users.
 */
@Repository
public interface UserRepository extends JpaRepository<UserEntity, UUID> {
    Optional<UserEntity> findByUsernameAndIsActiveTrue(String username);
    Optional<UserEntity> findByUsername(String username);
    Optional<UserEntity> findByEmployeeId(String employeeId);
}
