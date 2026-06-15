package com.enterprise.inventory.inventory.web.controller;

import com.enterprise.inventory.inventory.application.InventoryService;
import com.enterprise.inventory.inventory.infrastructure.config.JwtUtil;
import com.enterprise.inventory.inventory.web.dto.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
@RestController
@RequestMapping("/api/v1/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private static final String IDEMPOTENCY_HEADER = "X-Idempotency-Key";

    private final InventoryService inventoryService;
    private final JwtUtil           jwtUtil;

    @PostMapping("/tasks/generate")
    public ResponseEntity<String> generateTaskId() {
        String taskId = "TSK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        return ResponseEntity.ok(taskId);
    }

    @PreAuthorize("hasAuthority('CAN_PUTAWAY')")
    @PostMapping("/receive")
    public ResponseEntity<InventoryResponseDTO> receiveStock(
            @Valid @RequestBody ReceiveStockDTO request,
            @RequestHeader(IDEMPOTENCY_HEADER) String idempotencyKey,
            @RequestHeader("Authorization") String authHeader) {

        UUID performedBy = resolveUserId(authHeader);

        InventoryResponseDTO response = inventoryService.receiveStock(
                request.sku(),
                request.locationId(),
                request.containerId(),
                request.qty(),
                request.taskId(),
                performedBy,
                idempotencyKey);

        return ResponseEntity.ok(response);
    }
//idempotecy values  -> forwareded to service layer inwhich operation are wrapped in idempotency checks
    @PreAuthorize("hasAuthority('CAN_PUTAWAY')")
    @PostMapping("/receive/batch")
    public ResponseEntity<List<InventoryResponseDTO>> receiveStockBatch(
            @Valid @RequestBody BatchPutawayDTO request,
            @RequestHeader(IDEMPOTENCY_HEADER) String idempotencyKey,
            @RequestHeader("Authorization") String authHeader) {

        UUID performedBy = resolveUserId(authHeader);

        List<InventoryResponseDTO> response = inventoryService.receiveStockBatch(
                request, performedBy, idempotencyKey);

        return ResponseEntity.ok(response);
    }

    @PreAuthorize("hasAuthority('CAN_PICK')")
    @PostMapping("/pick/reserve")
    public ResponseEntity<InventoryResponseDTO> reserveStock(
            @Valid @RequestBody PickReserveDTO request,
            @RequestHeader(IDEMPOTENCY_HEADER) String idempotencyKey,
            @RequestHeader("Authorization") String authHeader) {

        InventoryResponseDTO response = inventoryService.reserveStock(request, resolveUserId(authHeader), idempotencyKey);
        return ResponseEntity.ok(response);
    }

    @PreAuthorize("hasAuthority('CAN_PICK')")
    @PostMapping("/pick/confirm")
    public ResponseEntity<InventoryResponseDTO> confirmPick(
            @Valid @RequestBody PickConfirmDTO request,
            @RequestHeader(IDEMPOTENCY_HEADER) String idempotencyKey,
            @RequestHeader("Authorization") String authHeader) {

        InventoryResponseDTO response = inventoryService.confirmPick(
                request, resolveUserId(authHeader), idempotencyKey);

        return ResponseEntity.ok(response);
    }

    @PreAuthorize("hasAuthority('CAN_PICK')")
    @PostMapping("/pick/release")
    public ResponseEntity<Void> releaseReservation(
            @Valid @RequestBody ReleaseReservationDTO request,
            @RequestHeader("Authorization") String authHeader) {

        inventoryService.releaseReservation(
                request.sku(), request.locationId(), request.containerId(), request.qty(),
                request.taskId(), resolveUserId(authHeader));

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

    @GetMapping("/movements/me")
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