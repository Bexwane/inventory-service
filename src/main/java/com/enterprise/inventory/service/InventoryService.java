package com.enterprise.inventory.service;

import com.enterprise.inventory.entity.InventoryJpaEntity;
import com.enterprise.inventory.repository.InventoryRepository;
import com.enterprise.inventory.entity.ReservationLockJpaEntity;
import com.enterprise.inventory.repository.ReservationLockRepository;
import com.enterprise.inventory.entity.StockMovementJpaEntity;
import com.enterprise.inventory.repository.StockMovementRepository;
import com.enterprise.inventory.dto.InventoryResponseDTO;
import com.enterprise.inventory.dto.PagedResponseDTO;
import com.enterprise.inventory.dto.PickConfirmDTO;
import com.enterprise.inventory.dto.PickReserveDTO;
import com.enterprise.inventory.dto.StockMovementResponseDTO;
import com.enterprise.inventory.dto.BatchPutawayDTO;
import com.enterprise.inventory.dto.ContainerPutawayDTO;
import com.enterprise.inventory.dto.ItemPutawayDTO;
import com.enterprise.inventory.dto.ReceiveStockDTO;
import com.enterprise.inventory.dto.ReleaseReservationDTO;
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
import com.enterprise.inventory.messaging.InventoryEvent;
import com.enterprise.inventory.exception.InsufficientStockException;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
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
    private final ReservationLockRepository reservationLockRepository;
    private final com.enterprise.inventory.config.AppProperties appProperties;

    private static final Set<String> ALLOWED_SORT_FIELDS =
            Set.of("sku", "qtyOnHand", "qtyReserved", "createdAt", "updatedAt");

    /**
     * Receives a single stock unit into a specific warehouse bin/container.
     * Wrapped in an idempotency block to prevent duplicate processing if a scanner re-submits a network request.
     * 
     * @param request The putaway details (SKU, qty, location).
     * @param performedBy The UUID of the worker performing the action.
     * @param idempotencyKey A unique token preventing duplicate execution.
     * @return InventoryResponseDTO containing the new stock levels.
     */
    @Transactional
    public InventoryResponseDTO receiveStock(ReceiveStockDTO request, UUID performedBy, String idempotencyKey) {

        return idempotencyService.getOrCompute(idempotencyKey, () -> {
            


            InventoryJpaEntity entity = getOrInitializeEntity(request.sku(), request.locationId(), request.containerId());

            entity.receiveStock(request.qty());
            inventoryRepository.save(entity);



            StockMovementJpaEntity movement = movementRepository.save(StockMovementJpaEntity.of(
                    StockMovementJpaEntity.MovementType.RECEIVE, request.sku(), null, request.locationId(), request.containerId(), request.qty(), request.taskId(), "PUTAWAY_TASK", performedBy
            ));



            applicationEventPublisher.publishEvent(new InventoryEvent(
                    InventoryEvent.Type.STOCK_RECEIVED, movement.getId(), request.sku(), request.locationId(), request.containerId(), request.qty(), request.taskId(), 0
            ));

            log.info("Stock received: sku={} location={} container={} amount={} task={}",
                    request.sku(), request.locationId(), request.containerId(), request.qty(), request.taskId());

            return toResponse(entity);
        }, InventoryResponseDTO.class);
    }

    /**
     * Processes a full batch of items being received simultaneously (e.g., scanning a full pallet).
     * Guarantees atomic all-or-nothing insertion.
     */
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

                    InventoryJpaEntity entity = getOrInitializeEntity(sku, destLocationId, containerId);

                    entity.receiveStock(amount);
                    inventoryRepository.save(entity);

                    StockMovementJpaEntity movement = movementRepository.save(StockMovementJpaEntity.of(
                            StockMovementJpaEntity.MovementType.RECEIVE, sku, sourceLocationId, destLocationId, containerId, amount, taskId, "PUTAWAY_TASK", performedBy
                    ));

                    applicationEventPublisher.publishEvent(new InventoryEvent(
                            InventoryEvent.Type.STOCK_RECEIVED, movement.getId(), sku, destLocationId, containerId, amount, taskId, 0
                    ));

                    log.info("Batch stock received: sku={} from={} toLoc={} container={} amount={} task={}",
                            sku, sourceLocationId, destLocationId, containerId, amount, taskId);

                    responses.add(toResponse(entity));
                }
            }
            return responses;
        }, new TypeReference<List<InventoryResponseDTO>>() {});
    }

    /**
     * Phase 1 of picking: Locks a specific quantity of inventory so no other worker can take it.
     * Generates a Reservation Lock in the database to guarantee safety across concurrent requests.
     */
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

            acquireReservationLock(request.taskId(), request.sku(), request.locationId(), request.containerId(), request.qty());

            movementRepository.save(StockMovementJpaEntity.of(
                    StockMovementJpaEntity.MovementType.RESERVE, request.sku(), request.locationId(), null, request.containerId(), request.qty(), request.taskId(), "PICK_TASK", performedBy
            ));

            log.info("Stock reserved: sku={} location={} qty={} task={}",
                    request.sku(), request.locationId(), request.qty(), request.taskId());

            return toResponse(entity);
        }, InventoryResponseDTO.class);
    }

    /**
     * Phase 2 of picking: Confirms that a worker actually picked the previously reserved inventory.
     * Supports "Short Picks" (picking less than was reserved if physical inventory is missing).
     * Strongly verifies the Reservation Lock to prevent security bypass.
     */
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

            int actualLockedQty = verifyReservationLock(request.taskId(), request.sku(), request.locationId(), request.containerId());

            if (request.reservedQty() != actualLockedQty) {
                throw new IllegalStateException("SECURITY VIOLATION: Task ID " + request.taskId() + " reserved " + actualLockedQty + " but attempted to confirm using " + request.reservedQty());
            }

            entity.confirmPick(request.reservedQty(), request.actualQty());
            inventoryRepository.save(entity);

            releaseReservationLock(request.taskId(), request.sku(), request.locationId(), request.containerId());

            boolean isShortPick = request.actualQty() < request.reservedQty();

            StockMovementJpaEntity movement = movementRepository.save(StockMovementJpaEntity.of(
                    StockMovementJpaEntity.MovementType.PICK, request.sku(), request.locationId(), null, request.containerId(), request.actualQty(), request.taskId(), "PICK_TASK", performedBy
            ));

            applicationEventPublisher.publishEvent(new InventoryEvent(
                    InventoryEvent.Type.PICK_CONFIRMED, movement.getId(), request.sku(), request.locationId(), request.containerId(), request.actualQty(), request.taskId(), 0
            ));

            if (isShortPick) {
                int discrepancy = request.reservedQty() - request.actualQty();
                applicationEventPublisher.publishEvent(new InventoryEvent(
                        InventoryEvent.Type.SHORT_PICK, movement.getId(), request.sku(), request.locationId(), request.containerId(), 0, request.taskId(), discrepancy
                ));
                log.warn("Short pick: sku={} reserved={} actual={} discrepancy={}",
                        request.sku(), request.reservedQty(), request.actualQty(), discrepancy);
            }

            log.info("Pick confirmed: sku={} location={} qty={} task={} shortPick={}",
                    request.sku(), request.locationId(), request.actualQty(),
                    request.taskId(), isShortPick);

            return toResponse(entity);
        }, InventoryResponseDTO.class);
    }

    /**
     * Cancels a pending reservation (e.g., if a worker decides not to pick the item).
     * Restores the 'qtyAvailable' back to the global pool and deletes the Reservation Lock.
     */
    @Transactional
    public void releaseReservation(ReleaseReservationDTO request, UUID performedBy) {

        Optional<InventoryJpaEntity> optionalEntity = inventoryRepository.findExactMatch(request.sku(), request.locationId(), request.containerId());
        if (optionalEntity.isEmpty()) {
            log.warn("Release reservation: no record found (already released?) sku={} location={} container={} qty={} task={}",
                    request.sku(), request.locationId(), request.containerId(), request.qty(), request.taskId());
            return;
        }

        InventoryJpaEntity entity = optionalEntity.get();
        entity.releaseReservation(request.qty());
        inventoryRepository.save(entity);

        releaseReservationLock(request.taskId(), request.sku(), request.locationId(), request.containerId());

        movementRepository.save(StockMovementJpaEntity.of(
                StockMovementJpaEntity.MovementType.RELEASE, request.sku(), request.locationId(), null, request.containerId(), request.qty(), request.taskId(), "PICK_TASK", performedBy
        ));

        log.info("Reservation released: sku={} location={} qty={} task={}", request.sku(), request.locationId(), request.qty(), request.taskId());
    }

    /**
     * Fetches a paginated, searchable, and sortable list of active inventory.
     * Designed for the global Inventory view on the React dashboard.
     */
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

    private InventoryJpaEntity getOrInitializeEntity(String sku, UUID locationId, UUID containerId) {
        return inventoryRepository.findExactMatch(sku, locationId, containerId)
                .orElseGet(() -> {
                    InventoryJpaEntity newRecord = new InventoryJpaEntity();
                    newRecord.setSku(sku);
                    newRecord.setLocationId(locationId);
                    newRecord.setContainerId(containerId);
                    newRecord.setQtyOnHand(0);
                    newRecord.setQtyReserved(0);
                    return newRecord;
                });
    }

    private String generateLockKey(String taskId, String sku, UUID locationId, UUID containerId) {
        return "reservation:lock:%s:%s:%s:%s".formatted(
                taskId, sku, locationId, containerId == null ? "NONE" : containerId
        );
    }

    private void acquireReservationLock(String taskId, String sku, UUID locationId, UUID containerId, int qty) {
        ReservationLockJpaEntity lockEntity = ReservationLockJpaEntity.builder()
                .taskId(taskId)
                .sku(sku)
                .locationId(locationId)
                .containerId(containerId)
                .qty(qty)
                .expiresAt(Instant.now().plus(appProperties.getSecurity().getLocks().getReservationTtl()))
                .build();
        reservationLockRepository.save(lockEntity);

        String lockKey = generateLockKey(taskId, sku, locationId, containerId);
        try {
            redisTemplate.opsForValue().set(lockKey, String.valueOf(qty), appProperties.getSecurity().getLocks().getReservationTtl());
        } catch (Exception e) {
            log.warn("Redis write failed for reservation lock, safely stored in DB: {}", e.getMessage());
        }
    }

    private int verifyReservationLock(String taskId, String sku, UUID locationId, UUID containerId) {
        String lockKey = generateLockKey(taskId, sku, locationId, containerId);

        String lockedQtyStr = null;
        boolean cacheValid = false;
        try {
            if (Boolean.TRUE.equals(redisTemplate.hasKey("wms:cache:valid"))) {
                cacheValid = true;
                lockedQtyStr = redisTemplate.opsForValue().get(lockKey);
            } else {
                log.warn("Redis Cache Validity Key missing (Amnesia detected). Falling back to Database.");
            }
        } catch (Exception e) {
            log.warn("Redis unavailable for reservation check. Falling back to Database: {}", e.getMessage());
        }

        if (cacheValid && lockedQtyStr != null) {
            return Integer.parseInt(lockedQtyStr);
        }

        var dbLock = reservationLockRepository.findLock(taskId, sku, locationId);
        if (dbLock.isEmpty()) {
            throw new IllegalStateException("SECURITY VIOLATION: No active reservation found for Task ID " + taskId + ". Pick denied.");
        }
        return dbLock.get().getQty();
    }

    private void releaseReservationLock(String taskId, String sku, UUID locationId, UUID containerId) {
        reservationLockRepository.deleteLock(taskId, sku, locationId);
        try {
            String lockKey = generateLockKey(taskId, sku, locationId, containerId);
            redisTemplate.delete(lockKey);
        } catch (Exception e) {
            log.warn("Failed to delete lock from Redis: {}", e.getMessage());
        }
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