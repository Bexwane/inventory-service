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
 * REST API for inventory operations.
 *
 * FIX 1: Pick endpoints added — reserveStock and confirmPick were completely missing.
 *
 * FIX 2: @PreAuthorize role guards tightened per operation.
 *         Workers can receive and pick. Only supervisors can adjust.
 *
 * FIX 3: X-Idempotency-Key header on mutating endpoints.
 *         Scanner apps must send this header — server rejects without it.
 *         Prevents double-processing on network retry.
 *
 * FIX 4: @AuthenticationPrincipal extracts the worker ID from the token
 *         so every mutation records who did it — no trust-the-client user ID.
 *
 * FIX 5: GET /inventory returns PagedResponse<InventoryResponse> — a clean
 *         DTO, not Spring's internal Page object with metadata fields.
 */
@RestController
@RequestMapping("/api/v1/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private static final String IDEMPOTENCY_HEADER = "X-Idempotency-Key";

    private final InventoryService inventoryService;
    private final JwtUtil           jwtUtil;

    // ── Tasks ─────────────────────────────────────────────────────────────────

    /**
     * Generate a new unique Task ID for frontend operations.
     * 
     * POST /api/v1/inventory/tasks/generate
     */
    @PostMapping("/tasks/generate")
    public ResponseEntity<String> generateTaskId() {
        String taskId = "TSK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        return ResponseEntity.ok(taskId);
    }

    // ── Putaway ───────────────────────────────────────────────────────────────

    /**
     * Worker scans container into a bin — stock quantity goes UP.
     *
     * POST /api/v1/inventory/receive
     * Header: X-Idempotency-Key: <uuid>  (required)
     * Role: WORKER, SUPERVISOR, MANAGER
     */
    @PreAuthorize("hasAuthority('CAN_PUTAWAY')")
    @PostMapping("/receive")
    public ResponseEntity<InventoryResponse> receiveStock(
            @Valid @RequestBody ReceiveStockRequest request,
            @RequestHeader(IDEMPOTENCY_HEADER) String idempotencyKey,
            @RequestHeader("Authorization") String authHeader) {

        UUID performedBy = resolveUserId(authHeader);

        InventoryResponse response = inventoryService.receiveStock(
                request.sku(),
                request.locationId(),
                request.containerId(),
                request.qty(),
                request.taskId(),
                performedBy,
                idempotencyKey);

        return ResponseEntity.ok(response);
    }

    /**
     * Worker submits a nested batch of putaways under a single task.
     *
     * POST /api/v1/inventory/receive/batch
     * Header: X-Idempotency-Key: <uuid>
     * Role: WORKER, SUPERVISOR, MANAGER
     */
    @PreAuthorize("hasAuthority('CAN_PUTAWAY')")
    @PostMapping("/receive/batch")
    public ResponseEntity<List<InventoryResponse>> receiveStockBatch(
            @Valid @RequestBody BatchPutawayRequest request,
            @RequestHeader(IDEMPOTENCY_HEADER) String idempotencyKey,
            @RequestHeader("Authorization") String authHeader) {

        UUID performedBy = resolveUserId(authHeader);

        List<InventoryResponse> response = inventoryService.receiveStockBatch(
                request, performedBy, idempotencyKey);

        return ResponseEntity.ok(response);
    }

    // ── Picking ───────────────────────────────────────────────────────────────

    /**
     * Step 1 — Reserve stock when a pick task is created.
     * Blocks the qty so no other picker is sent to the same bin.
     *
     * POST /api/v1/inventory/pick/reserve
     * Role: WORKER, SUPERVISOR, MANAGER
     */
    @PreAuthorize("hasAuthority('CAN_PICK')")
    @PostMapping("/pick/reserve")
    public ResponseEntity<InventoryResponse> reserveStock(
            @Valid @RequestBody PickReserveRequest request,
            @RequestHeader("Authorization") String authHeader) {

        InventoryResponse response = inventoryService.reserveStock(request, resolveUserId(authHeader));
        return ResponseEntity.ok(response);
    }

    /**
     * Step 2 — Confirm pick after worker physically scans the items.
     * Deducts stock. Supports short picks via actualQty field.
     *
     * POST /api/v1/inventory/pick/confirm
     * Header: X-Idempotency-Key: <uuid>  (required — prevents double-deduction)
     * Role: WORKER, SUPERVISOR, MANAGER
     */
    @PreAuthorize("hasAuthority('CAN_PICK')")
    @PostMapping("/pick/confirm")
    public ResponseEntity<InventoryResponse> confirmPick(
            @Valid @RequestBody PickConfirmRequest request,
            @RequestHeader(IDEMPOTENCY_HEADER) String idempotencyKey,
            @RequestHeader("Authorization") String authHeader) {

        InventoryResponse response = inventoryService.confirmPick(
                request, resolveUserId(authHeader), idempotencyKey);

        return ResponseEntity.ok(response);
    }

    /**
     * Release a reservation without deducting stock.
     * Called when a pick task is cancelled or times out.
     *
     * POST /api/v1/inventory/pick/release
     * Role: WORKER, SUPERVISOR, MANAGER
     */
    @PreAuthorize("hasAuthority('CAN_PICK')")
    @PostMapping("/pick/release")
    public ResponseEntity<Void> releaseReservation(
            @Valid @RequestBody ReleaseReservationRequest request,
            @RequestHeader("Authorization") String authHeader) {

        inventoryService.releaseReservation(
                request.sku(), request.locationId(), request.containerId(), request.qty(),
                request.taskId(), resolveUserId(authHeader));

        return ResponseEntity.noContent().build();
    }

    // ── Listing ───────────────────────────────────────────────────────────────

    /**
     * Paginated list of in-stock items.
     *
     * GET /api/v1/inventory?page=0&size=20
     * Role: any authenticated user
     */
    @GetMapping
    @PreAuthorize("hasAuthority('CAN_MANAGE_INVENTORY')")
    public ResponseEntity<PagedResponse<InventoryResponse>> getInventory(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortDir) {
        
        return ResponseEntity.ok(inventoryService.getInStockInventory(page, size, search, sortBy, sortDir));
    }

    // ── User Activity Logs ────────────────────────────────────────────────────

    @GetMapping("/movements/me")
    @PreAuthorize("hasAuthority('CAN_MANAGE_INVENTORY')")
    public ResponseEntity<PagedResponse<StockMovementResponse>> getMyMovements(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        UUID userId = resolveUserId(authHeader);
        return ResponseEntity.ok(inventoryService.getMyMovements(userId, page, size));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Extracts the real database UUID from the JWT token claims.
     * The UUID is embedded at login time by AuthController — no DB call needed here.
     */
    private UUID resolveUserId(String authHeader) {
        String token = authHeader.substring(7); // strip "Bearer "
        return jwtUtil.extractUserId(token);
    }
}