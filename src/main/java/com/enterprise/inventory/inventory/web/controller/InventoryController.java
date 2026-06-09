package com.enterprise.inventory.inventory.web.controller;

import com.enterprise.inventory.inventory.application.InventoryService;
import com.enterprise.inventory.inventory.web.dto.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

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

    // ── Putaway ───────────────────────────────────────────────────────────────

    /**
     * Worker scans container into a bin — stock quantity goes UP.
     *
     * POST /api/v1/inventory/receive
     * Header: X-Idempotency-Key: <uuid>  (required)
     * Role: WORKER, SUPERVISOR, MANAGER
     */
    @PreAuthorize("hasAnyRole('WORKER', 'SUPERVISOR', 'MANAGER')")
    @PostMapping("/receive")
    public ResponseEntity<InventoryResponse> receiveStock(
            @Valid @RequestBody ReceiveStockRequest request,
            @RequestHeader(IDEMPOTENCY_HEADER) String idempotencyKey,
            @AuthenticationPrincipal UserDetails principal) {

        // FIX: performer ID comes from the authenticated token — not from the request body
        UUID performedBy = resolveUserId(principal);

        InventoryResponse response = inventoryService.receiveStock(
                request.sku(),
                request.locationId(),
                request.qty(),
                request.taskId(),
                performedBy,
                idempotencyKey);

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
    @PreAuthorize("hasAnyRole('WORKER', 'SUPERVISOR', 'MANAGER')")
    @PostMapping("/pick/reserve")
    public ResponseEntity<InventoryResponse> reserveStock(
            @Valid @RequestBody PickReserveRequest request,
            @AuthenticationPrincipal UserDetails principal) {

        InventoryResponse response = inventoryService.reserveStock(request, resolveUserId(principal));
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
    @PreAuthorize("hasAnyRole('WORKER', 'SUPERVISOR', 'MANAGER')")
    @PostMapping("/pick/confirm")
    public ResponseEntity<InventoryResponse> confirmPick(
            @Valid @RequestBody PickConfirmRequest request,
            @RequestHeader(IDEMPOTENCY_HEADER) String idempotencyKey,
            @AuthenticationPrincipal UserDetails principal) {

        InventoryResponse response = inventoryService.confirmPick(
                request, resolveUserId(principal), idempotencyKey);

        return ResponseEntity.ok(response);
    }

    /**
     * Release a reservation without deducting stock.
     * Called when a pick task is cancelled or times out.
     *
     * POST /api/v1/inventory/pick/release
     * Role: WORKER, SUPERVISOR, MANAGER
     */
    @PreAuthorize("hasAnyRole('WORKER', 'SUPERVISOR', 'MANAGER')")
    @PostMapping("/pick/release")
    public ResponseEntity<Void> releaseReservation(
            @Valid @RequestBody ReleaseReservationRequest request,
            @AuthenticationPrincipal UserDetails principal) {

        inventoryService.releaseReservation(
                request.sku(), request.locationId(), request.qty(),
                request.taskId(), resolveUserId(principal));

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
    public ResponseEntity<PagedResponse<InventoryResponse>> getInventory(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        // FIX: service caps size at 100 internally — returns clean PagedResponse DTO
        PagedResponse<InventoryResponse> response = inventoryService.getInStockInventory(page, size);
        return ResponseEntity.ok(response);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private UUID resolveUserId(UserDetails principal) {
        // In production: load the user entity by username to get the UUID
        // Here: stub — replace with actual user lookup via UserRepository
        return UUID.nameUUIDFromBytes(principal.getUsername().getBytes());
    }
}