package com.enterprise.inventory.inventory.infrastructure.messaging;

import com.enterprise.inventory.inventory.application.InventoryEvent;
import com.enterprise.inventory.inventory.infrastructure.persistence.StockMovementRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Publisher service for sending inventory domain events to Kafka topics.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InventoryEventPublisher {

    static final String TOPIC_PUTAWAY_CONFIRMED = "wms.putaway.confirmed";
    static final String TOPIC_PICK_CONFIRMED    = "wms.pick.confirmed";
    static final String TOPIC_PICK_SHORT        = "wms.pick.short";

    private final KafkaTemplate<String, Map<String, Object>> kafkaTemplate;
    private final StockMovementRepository movementRepository;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async
    public void onInventoryEvent(InventoryEvent event) {
        switch (event.eventType()) {
            case STOCK_RECEIVED -> publishStockReceived(event.movementId(),
                    event.sku(), event.locationId(), event.containerId(),
                    event.qty(), event.taskId());
            case PICK_CONFIRMED -> publishPickConfirmed(event.movementId(),
                    event.sku(), event.locationId(), event.containerId(),
                    event.qty(), event.taskId());
            case SHORT_PICK -> publishShortPick(event.movementId(),
                    event.sku(), event.locationId(), event.containerId(),
                    event.taskId(), event.discrepancy());
        }
    }

    public void publishStockReceived(UUID movementId, String sku, UUID locationId, UUID containerId, int qty, String taskId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("eventType",   "STOCK_RECEIVED");
        payload.put("sku",         sku);
        payload.put("locationId",  locationId.toString());
        payload.put("containerId", containerId != null ? containerId.toString() : null);
        payload.put("qty",         qty);
        payload.put("taskId",      taskId);
        payload.put("occurredAt",  Instant.now().toString());
        publishAsync(TOPIC_PUTAWAY_CONFIRMED, sku, payload, movementId);
    }

    public void publishPickConfirmed(UUID movementId, String sku, UUID locationId, UUID containerId, int qty, String taskId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("eventType",   "PICK_CONFIRMED");
        payload.put("sku",         sku);
        payload.put("locationId",  locationId != null ? locationId.toString() : null);
        payload.put("containerId", containerId != null ? containerId.toString() : null);
        payload.put("qty",         qty);
        payload.put("taskId",      taskId);
        payload.put("occurredAt",  Instant.now().toString());
        publishAsync(TOPIC_PICK_CONFIRMED, sku, payload, movementId);
    }

    public void publishShortPick(UUID movementId, String sku, UUID locationId, UUID containerId, String taskId, int discrepancy) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("eventType",   "SHORT_PICK");
        payload.put("sku",         sku);
        payload.put("locationId",  locationId.toString());
        payload.put("containerId", containerId != null ? containerId.toString() : null);
        payload.put("taskId",      taskId);
        payload.put("discrepancy", discrepancy);
        payload.put("occurredAt",  Instant.now().toString());
        publishAsync(TOPIC_PICK_SHORT, sku, payload, movementId);
    }

    public void publishStockReceivedSync(String sku, UUID locationId, UUID containerId, int qty, String taskId)
            throws ExecutionException, InterruptedException, TimeoutException {
        Map<String, Object> payload = new HashMap<>();
        payload.put("eventType",   "STOCK_RECEIVED");
        payload.put("sku",         sku);
        payload.put("locationId",  locationId != null ? locationId.toString() : null);
        payload.put("containerId", containerId != null ? containerId.toString() : null);
        payload.put("qty",         qty);
        payload.put("taskId",      taskId);
        payload.put("occurredAt",  Instant.now().toString());
        payload.put("isRetry",     true);
        publishSync(TOPIC_PUTAWAY_CONFIRMED, sku, payload);
    }

    public void publishPickConfirmedSync(String sku, UUID locationId, UUID containerId, int qty, String taskId)
            throws ExecutionException, InterruptedException, TimeoutException {
        Map<String, Object> payload = new HashMap<>();
        payload.put("eventType",   "PICK_CONFIRMED");
        payload.put("sku",         sku);
        payload.put("locationId",  locationId != null ? locationId.toString() : null);
        payload.put("containerId", containerId != null ? containerId.toString() : null);
        payload.put("qty",         qty);
        payload.put("taskId",      taskId);
        payload.put("occurredAt",  Instant.now().toString());
        payload.put("isRetry",     true);
        publishSync(TOPIC_PICK_CONFIRMED, sku, payload);
    }

    private void publishAsync(String topic, String key, Map<String, Object> payload, UUID movementId) {
        kafkaTemplate.send(topic, key, payload)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Kafka publish failed topic={} key={}: {}", topic, key, ex.getMessage());
                    } else {
                        movementRepository.markSyncedToSap(movementId);
                        log.debug("Kafka published topic={} partition={} offset={}",
                                topic,
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    }
                });
    }

    private void publishSync(String topic, String key, Map<String, Object> payload)
            throws ExecutionException, InterruptedException, TimeoutException {
        kafkaTemplate.send(topic, key, payload).get(10, TimeUnit.SECONDS);
        log.debug("Kafka sync published topic={} key={}", topic, key);
    }
}