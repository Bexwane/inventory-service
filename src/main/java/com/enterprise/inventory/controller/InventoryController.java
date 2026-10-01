package com.enterprise.inventory.controller;

import com.enterprise.inventory.service.InventoryService;
import com.enterprise.inventory.security.JwtUtil;
import com.enterprise.inventory.dto.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST controller exposing endpoints for inventory operations, including tasks, putaway, picking, and releases.
 */
@Slf4j
@RestController
@RequestMapping("${app.api.base-path}${app.api.inventory.base}")
@RequiredArgsConstructor
public class InventoryController {

    private static final String IDEMPOTENCY_HEADER = "X-Idempotency-Key";

    private final InventoryService inventoryService;
    private final JwtUtil           jwtUtil;

    @PostMapping("${app.api.inventory.tasks-generate}")
    public ResponseEntity<String> generateTaskId() {
        String taskId = "TSK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        return ResponseEntity.ok(taskId);
    }

    @PreAuthorize("hasAuthority('CAN_PUTAWAY')")
    @PostMapping("${app.api.inventory.receive}")
    public ResponseEntity<InventoryResponseDTO> receiveStock(
            @Valid @RequestBody ReceiveStockDTO request,
            @RequestHeader(IDEMPOTENCY_HEADER) String idempotencyKey,
            @RequestHeader("Authorization") String authHeader) {

        UUID performedBy = resolveUserId(authHeader);

        InventoryResponseDTO response = inventoryService.receiveStock(
                request,
                performedBy,
                idempotencyKey);

        return ResponseEntity.ok(response);
    }

    @PreAuthorize("hasAuthority('CAN_PUTAWAY')")
    @PostMapping("${app.api.inventory.receive-batch}")
    public ResponseEntity<List<InventoryResponseDTO>> receiveStockBatch(
            @Valid @RequestBody BatchPutawayDTO request,
            @RequestHeader(IDEMPOTENCY_HEADER) String idempotencyKey,
            @RequestHeader("Authorization") String authHeader) {

        UUID performedBy = resolveUserId(authHeader);

        List<InventoryResponseDTO> response = inventoryService.receiveStockBatch(
                request, 
                performedBy, 
                idempotencyKey
        );

        return ResponseEntity.ok(response);
    }

    @PreAuthorize("hasAuthority('CAN_PICK')")
    @PostMapping("${app.api.inventory.pick-reserve}")
    public ResponseEntity<InventoryResponseDTO> reserveStock(
            @Valid @RequestBody PickReserveDTO request,
            @RequestHeader(IDEMPOTENCY_HEADER) String idempotencyKey,
            @RequestHeader("Authorization") String authHeader) {

        InventoryResponseDTO response = inventoryService.reserveStock(
                request, 
                resolveUserId(authHeader), 
                idempotencyKey
        );
        return ResponseEntity.ok(response);
    }

    @PreAuthorize("hasAuthority('CAN_PICK')")
    @PostMapping("${app.api.inventory.pick-confirm}")
    public ResponseEntity<InventoryResponseDTO> confirmPick(
            @Valid @RequestBody PickConfirmDTO request,
            @RequestHeader(IDEMPOTENCY_HEADER) String idempotencyKey,
            @RequestHeader("Authorization") String authHeader) {

        InventoryResponseDTO response = inventoryService.confirmPick(
                request, 
                resolveUserId(authHeader), 
                idempotencyKey
        );

        return ResponseEntity.ok(response);
    }

    @PreAuthorize("hasAuthority('CAN_PICK')")
    @PostMapping("${app.api.inventory.pick-release}")
    public ResponseEntity<Void> releaseReservation(
            @Valid @RequestBody ReleaseReservationDTO request,
            @RequestHeader("Authorization") String authHeader) {

        inventoryService.releaseReservation(
                request, 
                resolveUserId(authHeader)
        );

        return ResponseEntity.noContent().build();
    }

    @GetMapping
    @PreAuthorize("hasAuthority('CAN_MANAGE_INVENTORY')")
    public ResponseEntity<PagedResponseDTO<InventoryResponseDTO>> getInventory(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortDir) {
        
        return ResponseEntity.ok(inventoryService.getInStockInventory(page, size, search, sortBy, sortDir));
    }

    @GetMapping("${app.api.inventory.movements-me}")
    @PreAuthorize("hasAuthority('CAN_MANAGE_INVENTORY')")
    public ResponseEntity<PagedResponseDTO<StockMovementResponseDTO>> getMyMovements(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        UUID userId = resolveUserId(authHeader);
        return ResponseEntity.ok(inventoryService.getMyMovements(userId, page, size));
    }

    private UUID resolveUserId(String authHeader) {
        String token = authHeader.substring(7);
        return jwtUtil.extractUserId(token);
    }
}