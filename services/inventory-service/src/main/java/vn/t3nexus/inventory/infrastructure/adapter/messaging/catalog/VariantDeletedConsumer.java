package vn.t3nexus.inventory.infrastructure.adapter.messaging.catalog;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import vn.t3nexus.inventory.application.stock.DeleteStock;
import vn.t3nexus.lib.events.EventEnvelopeDecoder;
import vn.t3nexus.lib.events.EventEnvelopeMdcPropagator;
import vn.t3nexus.lib.events.OutboxEventData;

/**
 * Idempotency: DB-based, không dùng Redis — {@code DeleteStock.handle()} là DELETE tự thân, idempotent
 * tuyệt đối (xoá cái không tồn tại là no-op).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VariantDeletedConsumer {

    private final ObjectMapper objectMapper;
    private final EventEnvelopeDecoder decoder;
    private final DeleteStock deleteStock;

    @KafkaListener(
            topics  = "${app.kafka.topic.variant-deleted}",
            groupId = "${app.kafka.consumer-group.catalog}"
    )
    public void consume(String message) {
        OutboxEventData event = objectMapper.readValue(message, OutboxEventData.class);
        EventEnvelopeMdcPropagator.propagate(event.payload());

        try {
            Payload payload = decoder.decode(event, Payload.class);
            deleteStock.handle(new DeleteStock.Command(payload.skuId()));
        } catch (Exception e) {
            log.error("[VariantDeletedConsumer] failed to process eventId={}", event.payload().eventId(), e);
            throw e;
        } finally {
            EventEnvelopeMdcPropagator.clear();
        }
    }

    private record Payload(String skuId, String productId) {}
}
