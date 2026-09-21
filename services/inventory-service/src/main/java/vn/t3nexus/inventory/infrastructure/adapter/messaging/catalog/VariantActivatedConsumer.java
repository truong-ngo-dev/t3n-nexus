package vn.t3nexus.inventory.infrastructure.adapter.messaging.catalog;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import vn.t3nexus.inventory.application.stock.ActivateStock;
import vn.t3nexus.lib.events.EventEnvelopeDecoder;
import vn.t3nexus.lib.events.EventEnvelopeMdcPropagator;
import vn.t3nexus.lib.events.OutboxEventData;

/**
 * Idempotency: DB-based, không dùng Redis — {@code ActivateStock.handle()} tự idempotent qua fast-path
 * {@code isSellerActive()} + {@code @Version} (optimistic lock) trên Stock, không cần "khoá" ngoài nào.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VariantActivatedConsumer {

    private final ObjectMapper objectMapper;
    private final EventEnvelopeDecoder decoder;
    private final ActivateStock activateStock;

    @KafkaListener(
            topics  = "${app.kafka.topic.variant-activated}",
            groupId = "${app.kafka.consumer-group.catalog}"
    )
    public void consume(String message) {
        OutboxEventData event = objectMapper.readValue(message, OutboxEventData.class);
        EventEnvelopeMdcPropagator.propagate(event.payload());

        try {
            Payload payload = decoder.decode(event, Payload.class);
            activateStock.handle(new ActivateStock.Command(payload.skuId()));
        } catch (Exception e) {
            log.error("[VariantActivatedConsumer] failed to process eventId={}", event.payload().eventId(), e);
            throw e;
        } finally {
            EventEnvelopeMdcPropagator.clear();
        }
    }

    private record Payload(String skuId, String productId) {}
}
