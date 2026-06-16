package com.enterprise.inventory.inventory.application;

import com.enterprise.inventory.inventory.infrastructure.persistence.InventoryJpaEntity;
import com.enterprise.inventory.inventory.infrastructure.persistence.InventoryRepository;
import com.enterprise.inventory.inventory.infrastructure.persistence.StockMovementJpaEntity;
import com.enterprise.inventory.inventory.infrastructure.persistence.StockMovementRepository;
import com.enterprise.inventory.inventory.web.dto.InventoryResponseDTO;
import com.enterprise.inventory.inventory.web.dto.PagedResponseDTO;
import com.enterprise.inventory.inventory.web.dto.PickConfirmDTO;
import com.enterprise.inventory.inventory.web.dto.PickReserveDTO;
import com.enterprise.inventory.inventory.web.dto.StockMovementResponseDTO;
import com.enterprise.inventory.inventory.web.dto.BatchPutawayDTO;
import com.enterprise.inventory.inventory.web.dto.ContainerPutawayDTO;
import com.enterprise.inventory.inventory.web.dto.ItemPutawayDTO;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Service for managing core inventory operations such as stock receipt, reservation, picking, and releases.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryService {

    private final InventoryRepository     inventoryRepository;
    private final StockMovementRepository movementRepository;
    private final IdempotencyService      idempotencyService;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final StringRedisTemplate     redisTemplate;

    private static final Set<String> ALLOWED_SORT_FIELDS =
            Set.of("sku", "qtyOnHand", "qtyReserved", "createdAt", "updatedAt");

    @Transactional
    public InventoryResponseDTO receiveStock(String sku, 
                                             UUID locationId, 
                                             UUID containerId, 
                                             int amount,
                                             String taskId, 
                                             UUID performedBy,
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

            StockMovementJpaEntity movement = movementRepository.save(StockMovementJpaEntity.of(
                    StockMovementJpaEntity.MovementType.RECEIVE,
                    sku, 
                    null, 
                    locationId, 
                    containerId, 
                    amount,
                    taskId, 
                    "PUTAWAY_TASK", 
                    performedBy
            ));

            applicationEventPublisher.publishEvent(
                    new InventoryEvent(
                            InventoryEvent.Type.STOCK_RECEIVED, 
                            movement.getId(), 
                            sku, 
                            locationId, 
                            containerId, 
                            amount, 
                            taskId, 
                            0
                    )
            );

            log.info("Stock received: sku={} location={} container={} amount={} task={}",
                    sku, locationId, containerId, amount, taskId);

            return toResponse(entity);
        }, InventoryResponseDTO.class);
    }

    @Transactional
    public List<InventoryResponseDTO> receiveStockBatch(BatchPutawayDTO request, UUID performedBy, String idempotencyKey) {
        return idempotencyService.getOrCompute(idempotencyKey, () -> {
            List<InventoryResponseDTO> responses = new ArrayList<>();
            String taskId = request.taskId();
            UUID sourceLocationId = request.sourceLocationId();

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

                    StockMovementJpaEntity movement = movementRepository.save(StockMovementJpaEntity.of(
                            StockMovementJpaEntity.MovementType.RECEIVE,
                            sku, 
                            sourceLocationId, 
                            destLocationId, 
                            containerId, 
                            amount,
                            taskId, 
                            "PUTAWAY_TASK", 
                            performedBy
                    ));

                    applicationEventPublisher.publishEvent(
                            new InventoryEvent(
                                    InventoryEvent.Type.STOCK_RECEIVED, 
                                    movement.getId(), 
                                    sku, 
                                    destLocationId, 
                                    containerId, 
                                    amount, 
                                    taskId, 
                                    0
                            )
                    );

                    log.info("Batch stock received: sku={} from={} toLoc={} container={} amount={} task={}",
                            sku, sourceLocationId, destLocationId, containerId, amount, taskId);

                    responses.add(toResponse(entity));
                }
            }
            return responses;
        }, new TypeReference<List<InventoryResponseDTO>>() {});
    }

    @Transactional
    public InventoryResponseDTO reserveStock(PickReserveDTO request, UUID performedBy, String idempotencyKey) {

        return idempotencyService.getOrCompute(idempotencyKey, () -> {

            InventoryJpaEntity entity = inventoryRepository.findExactMatch(
                    request.sku(), request.locationId(), request.containerId())
                    .orElseThrow(() -> new InsufficientStockException(
                            "Inventory record missing for SKU " + request.sku()
                                    + " at location " + request.locationId()
                                    + " container " + request.containerId()));

            entity.reserveStock(request.qty());
            inventoryRepository.save(entity);

            // SECURITY FIX: Create a 2-hour reservation lock in Redis tied specifically to this Task ID.
            // This prevents other users from stealing this reservation during the confirm step.
            String lockKey = "reservation:lock:%s:%s:%s:%s".formatted(
                    request.taskId(),
                    request.sku(),
                    request.locationId(),
                    request.containerId() == null ? "NONE" : request.containerId()
            );
            redisTemplate.opsForValue().set(lockKey, String.valueOf(request.qty()), Duration.ofHours(2));

            movementRepository.save(StockMovementJpaEntity.of(
                    StockMovementJpaEntity.MovementType.RESERVE,
                    request.sku(), 
                    request.locationId(), 
                    null, 
                    request.containerId(), 
                    request.qty(),
                    request.taskId(), 
                    "PICK_TASK", 
                    performedBy
            ));

            log.info("Stock reserved: sku={} location={} qty={} task={}",
                    request.sku(), request.locationId(), request.qty(), request.taskId());

            return toResponse(entity);
        }, InventoryResponseDTO.class);
    }

    @Transactional
    public InventoryResponseDTO confirmPick(PickConfirmDTO request, UUID performedBy,
                                         String idempotencyKey) {

        return idempotencyService.getOrCompute(idempotencyKey, () -> {

            InventoryJpaEntity entity = inventoryRepository.findExactMatch(
                    request.sku(), request.locationId(), request.containerId())
                    .orElseThrow(() -> new IllegalStateException(
                            "Inventory record missing for SKU " + request.sku()
                                    + " at location " + request.locationId()
                                    + " container " + request.containerId()));

            // SECURITY FIX: Verify that THIS task actually holds a reservation lock in Redis.
            String lockKey = "reservation:lock:%s:%s:%s:%s".formatted(
                    request.taskId(),
                    request.sku(),
                    request.locationId(),
                    request.containerId() == null ? "NONE" : request.containerId()
            );
            String lockedQtyStr = redisTemplate.opsForValue().get(lockKey);
            if (lockedQtyStr == null) {
                throw new IllegalStateException("SECURITY VIOLATION: No active reservation found for Task ID " + request.taskId() + ". Pick denied.");
            }
            int actualLockedQty = Integer.parseInt(lockedQtyStr);
            if (request.reservedQty() != actualLockedQty) {
                throw new IllegalStateException("SECURITY VIOLATION: Task ID " + request.taskId() + " reserved " + actualLockedQty + " but attempted to confirm using " + request.reservedQty());
            }

            entity.confirmPick(request.reservedQty(), request.actualQty());
            inventoryRepository.save(entity);

            // Delete the lock since the reservation is fulfilled.
            redisTemplate.delete(lockKey);

            boolean isShortPick = request.actualQty() < request.reservedQty();

            StockMovementJpaEntity movement = movementRepository.save(StockMovementJpaEntity.of(
                    StockMovementJpaEntity.MovementType.PICK,
                    request.sku(), 
                    request.locationId(), 
                    null, 
                    request.containerId(), 
                    request.actualQty(),
                    request.taskId(), 
                    "PICK_TASK", 
                    performedBy
            ));

            applicationEventPublisher.publishEvent(
                    new InventoryEvent(
                            InventoryEvent.Type.PICK_CONFIRMED, 
                            movement.getId(), 
                            request.sku(),
                            request.locationId(), 
                            request.containerId(),
                            request.actualQty(), 
                            request.taskId(), 
                            0
                    )
            );

            if (isShortPick) {
                int discrepancy = request.reservedQty() - request.actualQty();
                applicationEventPublisher.publishEvent(
                        new InventoryEvent(
                                InventoryEvent.Type.SHORT_PICK, 
                                movement.getId(), 
                                request.sku(),
                                request.locationId(), 
                                request.containerId(),
                                0, 
                                request.taskId(), 
                                discrepancy
                        )
                );
                log.warn("Short pick: sku={} reserved={} actual={} discrepancy={}",
                        request.sku(), request.reservedQty(), request.actualQty(), discrepancy);
            }

            log.info("Pick confirmed: sku={} location={} qty={} task={} shortPick={}",
                    request.sku(), request.locationId(), request.actualQty(),
                    request.taskId(), isShortPick);

            return toResponse(entity);
        }, InventoryResponseDTO.class);
    }

    @Transactional
    public void releaseReservation(String sku, UUID locationId, UUID containerId, int qty,
                                   String taskId, UUID performedBy) {

        Optional<InventoryJpaEntity> optionalEntity = inventoryRepository.findExactMatch(sku, locationId, containerId);
        if (optionalEntity.isEmpty()) {
            log.warn("Release reservation: no record found (already released?) sku={} location={} container={} qty={} task={}",
                    sku, locationId, containerId, qty, taskId);
            return;
        }

        InventoryJpaEntity entity = optionalEntity.get();
        entity.releaseReservation(qty);
        inventoryRepository.save(entity);

        // Delete the Redis reservation lock to prevent zombie locks
        String lockKey = "reservation:lock:%s:%s:%s:%s".formatted(
                taskId, sku, locationId, containerId == null ? "NONE" : containerId
        );
        redisTemplate.delete(lockKey);

        movementRepository.save(StockMovementJpaEntity.of(
                StockMovementJpaEntity.MovementType.RELEASE,
                sku, 
                locationId, 
                null, 
                containerId, 
                qty,
                taskId, 
                "PICK_TASK", 
                performedBy
        ));

        log.info("Reservation released: sku={} location={} qty={} task={}", sku, locationId, qty, taskId);
    }

    @Transactional(readOnly = true)
    public PagedResponseDTO<InventoryResponseDTO> getInStockInventory(int page, int size, String search, String sortBy, String sortDir) {
        Sort sort = Sort.unsorted();
        if (sortBy != null && !sortBy.isEmpty()) {
            if (!ALLOWED_SORT_FIELDS.contains(sortBy)) {
                throw new IllegalArgumentException(
                        "Invalid sort field '" + sortBy + "'. Allowed values: " + ALLOWED_SORT_FIELDS);
            }
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

        List<InventoryResponseDTO> content = inventoryPage.getContent()
                .stream()
                .map(this::toResponse)
                .toList();

        return new PagedResponseDTO<>(
                content,
                inventoryPage.getNumber(),
                inventoryPage.getSize(),
                inventoryPage.getTotalElements(),
                inventoryPage.getTotalPages(),
                inventoryPage.isLast()
        );
    }

    @Transactional(readOnly = true)
    public PagedResponseDTO<StockMovementResponseDTO> getMyMovements(UUID userId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<StockMovementJpaEntity> pageResult = movementRepository.findByPerformedByOrderByOccurredAtDesc(userId, pageable);

        Page<StockMovementResponseDTO> responsePage = pageResult.map(entity -> new StockMovementResponseDTO(
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

        return PagedResponseDTO.from(responsePage);
    }

    private InventoryResponseDTO toResponse(InventoryJpaEntity entity) {
        return new InventoryResponseDTO(
                entity.getSku(),
                entity.getQtyOnHand(),
                entity.getQtyReserved(),
                entity.getQtyAvailable(),
                entity.getLocationId(),
                entity.getContainerId(),
                entity.getVersion());
    }
}