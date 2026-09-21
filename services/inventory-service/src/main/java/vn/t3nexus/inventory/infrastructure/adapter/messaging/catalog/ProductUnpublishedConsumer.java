package vn.t3nexus.inventory.infrastructure.adapter.messaging.catalog;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import vn.t3nexus.inventory.application.stock.DeactivateProductStocks;
import vn.t3nexus.lib.events.EventEnvelopeDecoder;
import vn.t3nexus.lib.events.EventEnvelopeMdcPropagator;
import vn.t3nexus.lib.events.OutboxEventData;

/**
 * Idempotency: DB-based, không dùng Redis — {@code DeactivateProductStocks.handle()} set field tuyệt
 * đối trên toàn bộ Stock của product, naturally idempotent, bảo vệ bởi {@code @Version} từng row.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProductUnpublishedConsumer {

    private final ObjectMapper objectMapper;
    private final EventEnvelopeDecoder decoder;
    private final DeactivateProductStocks deactivateProductStocks;

    @KafkaListener(
            topics  = "${app.kafka.topic.product-unpublished}",
            groupId = "${app.kafka.consumer-group.catalog}"
    )
    public void consume(String message) {
        OutboxEventData event = objectMapper.readValue(message, OutboxEventData.class);
        EventEnvelopeMdcPropagator.propagate(event.payload());

        try {
            Payload payload = decoder.decode(event, Payload.class);
            deactivateProductStocks.handle(new DeactivateProductStocks.Command(payload.productId()));
        } catch (Exception e) {
            log.error("[ProductUnpublishedConsumer] failed to process eventId={}", event.payload().eventId(), e);
            throw e;
        } finally {
            EventEnvelopeMdcPropagator.clear();
        }
    }

    private record Payload(String productId, String sellerId) {}
}
