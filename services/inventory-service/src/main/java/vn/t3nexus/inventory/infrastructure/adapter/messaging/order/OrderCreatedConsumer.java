package vn.t3nexus.inventory.infrastructure.adapter.messaging.order;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import vn.t3nexus.inventory.application.reservation.RecordReservationFailure;
import vn.t3nexus.inventory.application.reservation.ReserveInventory;
import vn.t3nexus.lib.events.EventEnvelopeDecoder;
import vn.t3nexus.lib.events.EventEnvelopeMdcPropagator;
import vn.t3nexus.lib.events.OutboxEventData;
import vn.t3nexus.lib.idempotency.IdempotencyGuard;

import java.time.Duration;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderCreatedConsumer {

    private static final Duration IDEMPOTENCY_TTL = Duration.ofDays(7);

    private final ObjectMapper objectMapper;
    private final EventEnvelopeDecoder decoder;
    private final IdempotencyGuard idempotencyGuard;
    private final ReserveInventory reserveInventory;
    private final RecordReservationFailure recordReservationFailure;

    @KafkaListener(
            topics  = "${app.kafka.topic.order-created}",
            groupId = "${app.kafka.consumer-group.order}"
    )
    public void consume(String message) {
        OutboxEventData event = objectMapper.readValue(message, OutboxEventData.class);
        EventEnvelopeMdcPropagator.propagate(event.payload());

        String idempotencyKey = "inv:order-created:" + event.payload().eventId();
        if (!idempotencyGuard.tryAcquire(idempotencyKey, IDEMPOTENCY_TTL)) {
            log.info("[OrderCreatedConsumer] duplicate eventId={}, skipping", event.payload().eventId());
            EventEnvelopeMdcPropagator.clear();
            return;
        }

        try {
            Payload payload = decoder.decode(event, Payload.class);
            ReserveInventory.Command command = new ReserveInventory.Command(
                    payload.orderId(),
                    payload.items().stream()
                            .map(item -> new ReserveInventory.Command.Item(item.skuId(), item.qty()))
                            .toList());

            try {
                reserveInventory.handle(command);
            } catch (ReserveInventory.ReservationFailedException e) {
                log.warn("[OrderCreatedConsumer] reservation failed orderId={}, failedSkuId={}, reason={}",
                        payload.orderId(), e.getFailedSkuId(), e.getReason());
                recordReservationFailure.handle(new RecordReservationFailure.Command(
                        payload.orderId(),
                        payload.items().stream()
                                .map(item -> new RecordReservationFailure.Command.Item(item.skuId(), item.qty()))
                                .toList(),
                        e.getFailedSkuId(),
                        e.getReason()));
            }
        } catch (Exception e) {
            idempotencyGuard.release(idempotencyKey);
            log.error("[OrderCreatedConsumer] failed to process eventId={}", event.payload().eventId(), e);
            throw e;
        } finally {
            EventEnvelopeMdcPropagator.clear();
        }
    }

    private record Payload(String orderId, String customerId, String sellerId,
                           List<Item> items, String paymentMethod) {
        private record Item(String skuId, int qty) {}
    }
}
