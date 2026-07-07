package vn.t3nexus.inventory.infrastructure.adapter.messaging.catalog;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import vn.t3nexus.inventory.application.stock.DeactivateStock;
import vn.t3nexus.lib.events.EventEnvelopeDecoder;
import vn.t3nexus.lib.events.EventEnvelopeMdcPropagator;
import vn.t3nexus.lib.events.OutboxEventData;
import vn.t3nexus.lib.idempotency.IdempotencyGuard;

import java.time.Duration;

@Slf4j
@Component
@RequiredArgsConstructor
public class VariantDeactivatedConsumer {

    private static final Duration IDEMPOTENCY_TTL = Duration.ofDays(7);

    private final ObjectMapper objectMapper;
    private final EventEnvelopeDecoder decoder;
    private final IdempotencyGuard idempotencyGuard;
    private final DeactivateStock deactivateStock;

    @KafkaListener(
            topics  = "${app.kafka.topic.variant-deactivated}",
            groupId = "${app.kafka.consumer-group.catalog}"
    )
    public void consume(String message) {
        OutboxEventData event = objectMapper.readValue(message, OutboxEventData.class);
        EventEnvelopeMdcPropagator.propagate(event.payload());

        String idempotencyKey = "inv:variant-deactivated:" + event.payload().eventId();
        if (!idempotencyGuard.tryAcquire(idempotencyKey, IDEMPOTENCY_TTL)) {
            log.info("[VariantDeactivatedConsumer] duplicate eventId={}, skipping", event.payload().eventId());
            EventEnvelopeMdcPropagator.clear();
            return;
        }

        try {
            Payload payload = decoder.decode(event, Payload.class);
            deactivateStock.handle(new DeactivateStock.Command(payload.skuId()));
        } catch (Exception e) {
            idempotencyGuard.release(idempotencyKey);
            log.error("[VariantDeactivatedConsumer] failed to process eventId={}", event.payload().eventId(), e);
            throw e;
        } finally {
            EventEnvelopeMdcPropagator.clear();
        }
    }

    private record Payload(String skuId, String productId) {}
}
