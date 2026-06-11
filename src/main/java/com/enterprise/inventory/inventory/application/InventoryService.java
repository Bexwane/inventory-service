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
import com.enterprise.inventory.inventory.web.dto.StockMovementResponse;
import com.enterprise.inventory.inventory.web.dto.BatchPutawayRequest;
import com.enterprise.inventory.inventory.web.dto.ContainerPutawayDTO;
import com.enterprise.inventory.inventory.web.dto.ItemPutawayDTO;
import com.fasterxml.jackson.core.type.TypeReference;
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
import java.util.List;
import java.util.ArrayList;

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
    public InventoryResponse receiveStock(String sku, UUID locationId, UUID containerId, int amount,
                                          String taskId, UUID performedBy,
                                          String idempotencyKey) {

        return idempotencyService.getOrCompute(idempotencyKey, () -> {

            InventoryJpaEntity entity = inventoryRepository.findExactMatch(sku, locationId, containerId)
                    .orElseGet(() -> {
                        InventoryJpaEntity newRecord = new InventoryJpaEntity();
                        newRecord.setSku(sku);
                        newRecord.setLocationId(locationId);
                        newRecord.setContainerId(containerId);
                        newRecord.setQtyOnHand(0);
                        newRecord.setQtyReserved(0);
                        return newRecord;
                    });

            entity.receiveStock(amount);
            inventoryRepository.save(entity);

            movementRepository.save(StockMovementJpaEntity.of(
                    StockMovementJpaEntity.MovementType.RECEIVE,
                    sku, null, locationId, containerId, amount,
                    taskId, "PUTAWAY_TASK", performedBy));

            eventPublisher.publishStockReceived(sku, locationId, containerId, amount, taskId);

            log.info("Stock received: sku={} location={} container={} amount={} task={}", sku, locationId, containerId, amount, taskId);

            return toResponse(entity);
        }, InventoryResponse.class);
    }

    @Transactional
    public List<InventoryResponse> receiveStockBatch(BatchPutawayRequest request, UUID performedBy, String idempotencyKey) {
        return idempotencyService.getOrCompute(idempotencyKey, () -> {
            List<InventoryResponse> responses = new ArrayList<>();
            String taskId = request.taskId();
            UUID sourceLocationId = request.sourceLocationId(); // mapped to fromLocationId

            for (ContainerPutawayDTO container : request.containers()) {
                UUID containerId = container.containerId();
                
                for (ItemPutawayDTO item : container.items()) {
                    String sku = item.sku();
                    UUID destLocationId = item.destinationLocationId();
                    int amount = item.qty();

                    InventoryJpaEntity entity = inventoryRepository.findExactMatch(sku, destLocationId, containerId)
                            .orElseGet(() -> {
                                InventoryJpaEntity newRecord = new InventoryJpaEntity();
                                newRecord.setSku(sku);
                                newRecord.setLocationId(destLocationId);
                                newRecord.setContainerId(containerId);
                                newRecord.setQtyOnHand(0);
                                newRecord.setQtyReserved(0);
                                return newRecord;
                            });

                    entity.receiveStock(amount);
                    inventoryRepository.save(entity);

                    movementRepository.save(StockMovementJpaEntity.of(
                            StockMovementJpaEntity.MovementType.RECEIVE,
                            sku, sourceLocationId, destLocationId, containerId, amount,
                            taskId, "PUTAWAY_TASK", performedBy));

                    eventPublisher.publishStockReceived(sku, destLocationId, containerId, amount, taskId);
                    log.info("Batch stock received: sku={} from={} toLoc={} container={} amount={} task={}", 
                            sku, sourceLocationId, destLocationId, containerId, amount, taskId);

                    responses.add(toResponse(entity));
                }
            }
            return responses;
        }, new TypeReference<List<InventoryResponse>>() {});
    }

    // ── Picking — step 1: reserve ─────────────────────────────────────────────

    @Transactional
    public InventoryResponse reserveStock(PickReserveRequest request, UUID performedBy) {

        InventoryJpaEntity entity = inventoryRepository.findExactMatch(request.sku(), request.locationId(), request.containerId())
                .orElseThrow(() -> new InsufficientStockException(
                        "Inventory record missing for SKU " + request.sku() + " at location " + request.locationId() + " container " + request.containerId()));

        entity.reserveStock(request.qty());
        inventoryRepository.save(entity);

        movementRepository.save(StockMovementJpaEntity.of(
                StockMovementJpaEntity.MovementType.RESERVE,
                request.sku(), request.locationId(), null, request.containerId(), request.qty(),
                request.taskId(), "PICK_TASK", performedBy));

        return toResponse(entity);
    }

    // ── Picking — step 2: confirm ─────────────────────────────────────────────

    @Transactional
    public InventoryResponse confirmPick(PickConfirmRequest request, UUID performedBy,
                                         String idempotencyKey) {

        return idempotencyService.getOrCompute(idempotencyKey, () -> {

            InventoryJpaEntity entity = inventoryRepository.findExactMatch(request.sku(), request.locationId(), request.containerId())
                    .orElseThrow(() -> new IllegalStateException(
                            "Inventory record missing for SKU " + request.sku() + " at location " + request.locationId() + " container " + request.containerId()));

            entity.confirmPick(request.reservedQty(), request.actualQty());
            inventoryRepository.save(entity);

            boolean isShortPick = request.actualQty() < request.reservedQty();

            movementRepository.save(StockMovementJpaEntity.of(
                    StockMovementJpaEntity.MovementType.PICK,
                    request.sku(), request.locationId(), null, request.containerId(), request.actualQty(),
                    request.taskId(), "PICK_TASK", performedBy));

            if (isShortPick) {
                int discrepancy = request.reservedQty() - request.actualQty();
                eventPublisher.publishShortPick(request.sku(), request.locationId(), request.containerId(),
                        request.taskId(), discrepancy);
                log.warn("Short pick detected: sku={} reserved={} actual={} discrepancy={}",
                        request.sku(), request.reservedQty(), request.actualQty(), discrepancy);
            }

            eventPublisher.publishPickConfirmed(request.sku(), request.locationId(), request.containerId(),
                    request.actualQty(), request.taskId());

            log.info("Pick confirmed: sku={} location={} qty={} task={} shortPick={}",
                    request.sku(), request.locationId(), request.actualQty(),
                    request.taskId(), isShortPick);

            return toResponse(entity);
        }, InventoryResponse.class);
    }

    // ── Reservation release (task cancelled / timed out) ─────────────────────

    @Transactional
    public void releaseReservation(String sku, UUID locationId, UUID containerId, int qty,
                                   String taskId, UUID performedBy) {

        Optional<InventoryJpaEntity> optionalEntity = inventoryRepository.findExactMatch(sku, locationId, containerId);
        if (optionalEntity.isEmpty()) {
            log.warn("Release reservation found no record: sku={} location={} container={} qty={} task={}",
                    sku, locationId, containerId, qty, taskId);
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
                sku, null, null, containerId, qty,
                taskId, "PICK_TASK", performedBy));

        log.info("Reservation released: sku={} location={} qty={} task={}", sku, locationId, qty, taskId);
    }

    // ── Listing ───────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public PagedResponse<InventoryResponse> getInStockInventory(int page, int size, String search, String sortBy, String sortDir) {
        Sort sort = Sort.unsorted();
        if (sortBy != null && !sortBy.isEmpty()) {
            Sort.Direction direction = "desc".equalsIgnoreCase(sortDir) ? Sort.Direction.DESC : Sort.Direction.ASC;
            sort = Sort.by(direction, sortBy);
        }
        
        PageRequest pageRequest = PageRequest.of(page, size, sort);
        Page<InventoryJpaEntity> inventoryPage;
        
        if (search != null && !search.trim().isEmpty()) {
            inventoryPage = inventoryRepository.searchAvailableInventory(search.trim(), pageRequest);
        } else {
            inventoryPage = inventoryRepository.findByQtyOnHandGreaterThan(0, pageRequest);
        }
        
        List<InventoryResponse> content = inventoryPage.getContent()
                .stream()
                .map(this::toResponse)
                .toList();

        return new PagedResponse<>(
                content,
                inventoryPage.getNumber(),
                inventoryPage.getSize(),
                inventoryPage.getTotalElements(),
                inventoryPage.getTotalPages(),
                inventoryPage.isLast()
        );
    }

    @Transactional(readOnly = true)
    public PagedResponse<StockMovementResponse> getMyMovements(UUID userId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<StockMovementJpaEntity> pageResult = movementRepository.findByPerformedByOrderByOccurredAtDesc(userId, pageable);

        Page<StockMovementResponse> responsePage = pageResult.map(entity -> new StockMovementResponse(
                entity.getId(),
                entity.getMovementType().name(),
                entity.getSku(),
                entity.getFromLocationId(),
                entity.getToLocationId(),
                entity.getContainerId(),
                entity.getQty(),
                entity.getReferenceId(),
                entity.getReferenceType(),
                entity.getOccurredAt(),
                entity.isSyncedToSap()
        ));

        return PagedResponse.from(responsePage);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private InventoryResponse toResponse(InventoryJpaEntity entity) {
        return new InventoryResponse(
                entity.getSku(),
                entity.getQtyOnHand(),
                entity.getQtyReserved(),
                entity.getQtyAvailable(),
                entity.getLocationId(),
                entity.getContainerId(),
                entity.getVersion()
        );}
}