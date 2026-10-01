package com.enterprise.inventory.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReservationLockId implements Serializable {
    private String taskId;
    private String sku;
    private UUID locationId;
}
