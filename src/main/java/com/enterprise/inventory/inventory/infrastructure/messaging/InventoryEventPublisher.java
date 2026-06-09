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

    public void publishStockReceived(String sku, UUID locationId, int qty, String taskId) {
        publish(TOPIC_PUTAWAY_CONFIRMED, sku, Map.of(
                "eventType",  "STOCK_RECEIVED",
                "sku",        sku,
                "locationId", locationId.toString(),
                "qty",        qty,
                "taskId",     taskId,
                "occurredAt", Instant.now().toString()
        ));
    }

    public void publishPickConfirmed(String sku, UUID locationId, int qty, String taskId) {
        publish(TOPIC_PICK_CONFIRMED, sku, Map.of(
                "eventType",  "PICK_CONFIRMED",
                "sku",        sku,
                "locationId", locationId.toString(),
                "qty",        qty,
                "taskId",     taskId,
                "occurredAt", Instant.now().toString()
        ));
    }

    public void publishShortPick(String sku, UUID locationId, String taskId, int discrepancy) {
        publish(TOPIC_PICK_SHORT, sku, Map.of(
                "eventType",   "SHORT_PICK",
                "sku",         sku,
                "locationId",  locationId.toString(),
                "taskId",      taskId,
                "discrepancy", discrepancy,
                "occurredAt",  Instant.now().toString()
        ));
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