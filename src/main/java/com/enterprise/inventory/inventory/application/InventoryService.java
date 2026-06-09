package com.enterprise.inventory.inventory.application;

import com.enterprise.inventory.inventory.infrastructure.messaging.InventoryEventPublisher;
import com.enterprise.inventory.inventory.infrastructure.persistence.InventoryJpaEntity;
import com.enterprise.inventory.inventory.infrastructure.persistence.InventoryRepository;
import com.enterprise.inventory.inventory.infrastructure.persistence.StockMovementJpaEntity;
import com.enterprise.inventory.inventory.infrastructure.persistence.StockMovementRepository;
import com.enterprise.inventory.inventory.web.dto.InventoryResponse;
import com.enterprise.inventory.inventory.web.dto.PagedResponse;
import com.enterprise.inventory.inventory.web.dto.PickConfirmRequest;
import com.enterprise.inventory.inventory.web.dto.PickReserveRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Core inventory operations: receive (putaway), reserve, confirm pick.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryService {

    private final InventoryRepository       inventoryRepository;
    private final StockMovementRepository   movementRepository;
    private final InventoryEventPublisher   eventPublisher;
    private final IdempotencyService        idempotencyService;

    // ── Putaway (receive stock) ───────────────────────────────────────────────

    @Transactional
    public InventoryResponse receiveStock(String sku, UUID locationId, int amount,
                                          String taskId, UUID performedBy,
                                          String idempotencyKey) {

        return idempotencyService.getOrCompute(idempotencyKey, () -> {

            InventoryJpaEntity entity = inventoryRepository.findBySkuAndLocationId(sku, locationId)
                    .orElseGet(() -> {
                        InventoryJpaEntity newRecord = new InventoryJpaEntity();
                        newRecord.setSku(sku);
                        newRecord.setLocationId(locationId);
                        newRecord.setQtyOnHand(0);
                        newRecord.setQtyReserved(0);
                        return newRecord;
                    });

            entity.receiveStock(amount);
            inventoryRepository.save(entity);

            movementRepository.save(StockMovementJpaEntity.of(
                    StockMovementJpaEntity.MovementType.RECEIVE,
                    sku, null, locationId, null, amount,
                    taskId, "PUTAWAY_TASK", performedBy));

            eventPublisher.publishStockReceived(sku, locationId, amount, taskId);

            log.info("Stock received: sku={} location={} amount={} task={}", sku, locationId, amount, taskId);

            return toResponse(entity);
        });
    }

    // ── Picking — step 1: reserve ─────────────────────────────────────────────

    @Transactional
    public InventoryResponse reserveStock(PickReserveRequest request, UUID performedBy) {

        InventoryJpaEntity entity = inventoryRepository.findBySkuAndLocationId(request.sku(), request.locationId())
                .orElseThrow(() -> new InsufficientStockException(
                        "Inventory record missing for SKU " + request.sku() + " at location " + request.locationId()));

        entity.reserveStock(request.qty());
        inventoryRepository.save(entity);

        movementRepository.save(StockMovementJpaEntity.of(
                StockMovementJpaEntity.MovementType.RESERVE,
                request.sku(), request.locationId(), null, null, request.qty(),
                request.taskId(), "PICK_TASK", performedBy));

        return toResponse(entity);
    }

    // ── Picking — step 2: confirm ─────────────────────────────────────────────

    @Transactional
    public InventoryResponse confirmPick(PickConfirmRequest request, UUID performedBy,
                                         String idempotencyKey) {

        return idempotencyService.getOrCompute(idempotencyKey, () -> {

            InventoryJpaEntity entity = inventoryRepository.findBySkuAndLocationId(request.sku(), request.locationId())
                    .orElseThrow(() -> new IllegalStateException(
                            "Inventory record missing for SKU " + request.sku() + " at location " + request.locationId()));

            entity.confirmPick(request.reservedQty(), request.actualQty());
            inventoryRepository.save(entity);

            boolean isShortPick = request.actualQty() < request.reservedQty();

            movementRepository.save(StockMovementJpaEntity.of(
                    StockMovementJpaEntity.MovementType.PICK,
                    request.sku(), request.locationId(), null, null, request.actualQty(),
                    request.taskId(), "PICK_TASK", performedBy));

            if (isShortPick) {
                int discrepancy = request.reservedQty() - request.actualQty();
                eventPublisher.publishShortPick(request.sku(), request.locationId(),
                        request.taskId(), discrepancy);
                log.warn("Short pick detected: sku={} reserved={} actual={} discrepancy={}",
                        request.sku(), request.reservedQty(), request.actualQty(), discrepancy);
            }

            eventPublisher.publishPickConfirmed(request.sku(), request.locationId(),
                    request.actualQty(), request.taskId());

            log.info("Pick confirmed: sku={} location={} qty={} task={} shortPick={}",
                    request.sku(), request.locationId(), request.actualQty(),
                    request.taskId(), isShortPick);

            return toResponse(entity);
        });
    }

    // ── Reservation release (task cancelled / timed out) ─────────────────────

    @Transactional
    public void releaseReservation(String sku, UUID locationId, int qty,
                                   String taskId, UUID performedBy) {

        Optional<InventoryJpaEntity> optionalEntity = inventoryRepository.findBySkuAndLocationId(sku, locationId);
        if (optionalEntity.isEmpty()) {
            log.warn("Release reservation found no record: sku={} location={} qty={} task={}",
                    sku, locationId, qty, taskId);
            return;
        }

        InventoryJpaEntity entity = optionalEntity.get();
        
        try {
            entity.releaseReservation(qty);
            inventoryRepository.save(entity);
        } catch (IllegalArgumentException e) {
            log.warn("Failed to release reservation: {}", e.getMessage());
            return;
        }

        movementRepository.save(StockMovementJpaEntity.of(
                StockMovementJpaEntity.MovementType.RELEASE,
                sku, null, null, null, qty,
                taskId, "PICK_TASK", performedBy));

        log.info("Reservation released: sku={} location={} qty={} task={}", sku, locationId, qty, taskId);
    }

    // ── Listing ───────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public PagedResponse<InventoryResponse> getInStockInventory(int page, int size) {
        int safeSize = Math.min(size, 100);
        Pageable pageable = PageRequest.of(page, safeSize, Sort.by("sku").ascending());
        Page<InventoryJpaEntity> entityPage =
                inventoryRepository.findByQtyOnHandGreaterThan(0, pageable);

        return PagedResponse.from(entityPage.map(this::toResponse));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private InventoryResponse toResponse(InventoryJpaEntity e) {
        return new InventoryResponse(
                e.getSku(),
                e.getQtyOnHand(),
                e.getQtyReserved(),
                e.getQtyAvailable(),
                e.getLocationId(),
                e.getVersion());
    }
}