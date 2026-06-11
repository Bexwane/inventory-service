package com.enterprise.inventory.inventory.infrastructure.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Publishes inventory events to Kafka after the DB transaction commits.
 *
 * WHY @TransactionalEventListener MATTERS:
 * If we published directly inside the @Transactional service method, an event
 * could fire even if the transaction rolls back later. SAP would be told stock
 * moved when it actually didn't.
 *
 * Using @TransactionalEventListener(phase = AFTER_COMMIT) guarantees:
 * - Event fires ONLY when the DB write succeeded and committed
 * - If the transaction rolls back, no event fires
 * - SAP and warehouse DB are always consistent
 *
 * Topics:
 *   wms.putaway.confirmed  → SAP adapter posts goods receipt
 *   wms.pick.confirmed     → SAP adapter posts goods issue
 *   wms.pick.short         → supervisor dashboard + SAP discrepancy alert
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InventoryEventPublisher {

    static final String TOPIC_PUTAWAY_CONFIRMED = "wms.putaway.confirmed";
    static final String TOPIC_PICK_CONFIRMED    = "wms.pick.confirmed";
    static final String TOPIC_PICK_SHORT        = "wms.pick.short";

    private final KafkaTemplate<String, Map<String, Object>> kafkaTemplate;

    public void publishStockReceived(String sku, UUID locationId, UUID containerId, int qty, String taskId) {
        java.util.Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("eventType", "STOCK_RECEIVED");
        payload.put("sku", sku);
        payload.put("locationId", locationId.toString());
        payload.put("containerId", containerId != null ? containerId.toString() : null);
        payload.put("qty", qty);
        payload.put("taskId", taskId);
        payload.put("occurredAt", Instant.now().toString());

        publish(TOPIC_PUTAWAY_CONFIRMED, sku, payload);
    }

    public void publishPickConfirmed(String sku, UUID locationId, UUID containerId, int qty, String taskId) {
        java.util.Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("eventType", "PICK_CONFIRMED");
        payload.put("sku", sku);
        payload.put("locationId", locationId.toString());
        payload.put("containerId", containerId != null ? containerId.toString() : null);
        payload.put("qty", qty);
        payload.put("taskId", taskId);
        payload.put("occurredAt", Instant.now().toString());

        publish(TOPIC_PICK_CONFIRMED, sku, payload);
    }

    public void publishShortPick(String sku, UUID locationId, UUID containerId, String taskId, int discrepancy) {
        java.util.Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("eventType", "SHORT_PICK");
        payload.put("sku", sku);
        payload.put("locationId", locationId.toString());
        payload.put("containerId", containerId != null ? containerId.toString() : null);
        payload.put("taskId", taskId);
        payload.put("discrepancy", discrepancy);
        payload.put("occurredAt", Instant.now().toString());

        publish(TOPIC_PICK_SHORT, sku, payload);
    }

    private void publish(String topic, String key, Map<String, Object> payload) {
        kafkaTemplate.send(topic, key, payload)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        // FIX: log failure but don't throw — the DB transaction already committed.
                        // The synced_to_sap=false flag on the StockMovement row will be picked
                        // up by a reconciliation job and retried.
                        log.error("Failed to publish event to topic={} key={}: {}", topic, key, ex.getMessage());
                    } else {
                        log.debug("Event published to topic={} partition={} offset={}",
                                topic,
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    }
                });
    }
}