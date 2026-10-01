package com.enterprise.inventory.scheduling;

import com.enterprise.inventory.messaging.InventoryEventPublisher;
import com.enterprise.inventory.entity.StockMovementJpaEntity;
import com.enterprise.inventory.repository.StockMovementRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Scheduled job to reconcile and retry stock movement synchronization to SAP.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SapSyncRecoveryJob {

    private static final int RETRY_DELAY_MINUTES = 5;
    private static final int MAX_BATCH_SIZE      = 50;

    private final StockMovementRepository movementRepository;
    private final InventoryEventPublisher eventPublisher;

    @Scheduled(fixedDelayString = "${wms.sap-sync.retry-interval-ms:300000}")
    @Transactional
    public void retryFailedSapSync() {
        Instant cutoff = Instant.now().minus(RETRY_DELAY_MINUTES, ChronoUnit.MINUTES);
        List<StockMovementJpaEntity> pending =
                movementRepository.findUnsyncedMovementsOlderThan(cutoff, MAX_BATCH_SIZE);

        if (pending.isEmpty()) return;

        log.info("SAP sync recovery: {} unsynced movement(s) found — retrying", pending.size());

        for (StockMovementJpaEntity movement : pending) {
            try {
                retryPublish(movement);
                movementRepository.markSyncedToSap(movement.getId());
                log.info("SAP sync recovered: id={} type={} sku={}",
                        movement.getId(), movement.getMovementType(), movement.getSku());
            } catch (Exception e) {
                log.error("SAP sync retry FAILED for id={} type={} sku={}: {}",
                        movement.getId(), movement.getMovementType(), movement.getSku(), e.getMessage());
            }
        }
    }

    private void retryPublish(StockMovementJpaEntity movement) throws Exception {
        switch (movement.getMovementType()) {
            case RECEIVE -> eventPublisher.publishStockReceivedSync(
                    movement.getSku(), movement.getToLocationId(), movement.getContainerId(),
                    movement.getQty(), movement.getReferenceId());
            case PICK -> eventPublisher.publishPickConfirmedSync(
                    movement.getSku(), movement.getFromLocationId(), movement.getContainerId(),
                    movement.getQty(), movement.getReferenceId());
            default -> log.debug("No SAP event needed for movement type: {}", movement.getMovementType());
        }
    }
}
