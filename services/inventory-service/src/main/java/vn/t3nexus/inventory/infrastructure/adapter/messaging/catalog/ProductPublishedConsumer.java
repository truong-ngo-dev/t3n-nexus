package vn.t3nexus.inventory.infrastructure.adapter.messaging.catalog;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import vn.t3nexus.inventory.application.stock.PublishProductStocks;
import vn.t3nexus.lib.events.EventEnvelopeDecoder;
import vn.t3nexus.lib.events.EventEnvelopeMdcPropagator;
import vn.t3nexus.lib.events.OutboxEventData;
import vn.t3nexus.lib.idempotency.IdempotencyGuard;

import java.time.Duration;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProductPublishedConsumer {

    private static final Duration IDEMPOTENCY_TTL = Duration.ofDays(7);

    private final ObjectMapper objectMapper;
    private final EventEnvelopeDecoder decoder;
    private final IdempotencyGuard idempotencyGuard;
    private final PublishProductStocks publishProductStocks;

    @KafkaListener(
            topics  = "${app.kafka.topic.product-published}",
            groupId = "${app.kafka.consumer-group.catalog}"
    )
    public void consume(String message) {
        OutboxEventData event = objectMapper.readValue(message, OutboxEventData.class);
        EventEnvelopeMdcPropagator.propagate(event.payload());

        String idempotencyKey = "inv:product-published:" + event.payload().eventId();
        if (!idempotencyGuard.tryAcquire(idempotencyKey, IDEMPOTENCY_TTL)) {
            log.info("[ProductPublishedConsumer] duplicate eventId={}, skipping", event.payload().eventId());
            EventEnvelopeMdcPropagator.clear();
            return;
        }

        try {
            Payload payload = decoder.decode(event, Payload.class);
            publishProductStocks.handle(new PublishProductStocks.Command(payload.productId()));
        } catch (Exception e) {
            idempotencyGuard.release(idempotencyKey);
            log.error("[ProductPublishedConsumer] failed to process eventId={}", event.payload().eventId(), e);
            throw e;
        } finally {
            EventEnvelopeMdcPropagator.clear();
        }
    }

    private record Payload(String productId) {}
}
