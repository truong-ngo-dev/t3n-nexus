package vn.t3nexus.inventory.infrastructure.adapter.messaging.order;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import vn.t3nexus.inventory.application.reservation.ReleaseReservation;
import vn.t3nexus.lib.events.EventEnvelopeDecoder;
import vn.t3nexus.lib.events.EventEnvelopeMdcPropagator;
import vn.t3nexus.lib.events.OutboxEventData;

/**
 * Idempotency: DB-based, không dùng Redis — cùng lý do đã áp dụng cho {@code OrderCreatedConsumer}.
 * {@code ReleaseReservation.handle()} tự idempotent qua {@code isPending()} + khoá pessimistic
 * ({@code findByOrderIdForUpdate}/{@code findBySkuIdForUpdate}) để chặn race khi 2 lần gọi cùng orderId
 * chạy gần như đồng thời. Không có "khoá" Redis nào có thể rò rỉ.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderCancelledConsumer {

    private final ObjectMapper objectMapper;
    private final EventEnvelopeDecoder decoder;
    private final ReleaseReservation releaseReservation;

    @KafkaListener(
            topics  = "${app.kafka.topic.order-cancelled}",
            groupId = "${app.kafka.consumer-group.order}"
    )
    public void consume(String message) {
        OutboxEventData event = objectMapper.readValue(message, OutboxEventData.class);
        EventEnvelopeMdcPropagator.propagate(event.payload());

        try {
            Payload payload = decoder.decode(event, Payload.class);
            releaseReservation.handle(new ReleaseReservation.Command(payload.orderId()));
        } catch (Exception e) {
            log.error("[OrderCancelledConsumer] failed to process eventId={}", event.payload().eventId(), e);
            throw e;
        } finally {
            EventEnvelopeMdcPropagator.clear();
        }
    }

    private record Payload(String orderId, String reason, String cancelledBy) {}
}
