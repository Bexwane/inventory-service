package com.enterprise.inventory.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;
import java.util.Set;
import java.util.HashSet;

/**
 * JPA entity representing a warehouse user account, including role and permission sets.
 */
@Entity
@Table(name = "users")
@Getter
@Setter
public class UserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "employee_id", unique = true)
    private String employeeId;

    @Column(name = "username", unique = true, nullable = false)
    private String username;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "role", nullable = false)
    @Enumerated(EnumType.STRING)
    private Role role;

    @Column(name = "is_active", nullable = false)
    private boolean isActive;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "user_permissions", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "permissions")
    @Enumerated(EnumType.STRING)
    private Set<Permission> permissions = new HashSet<>();
}
