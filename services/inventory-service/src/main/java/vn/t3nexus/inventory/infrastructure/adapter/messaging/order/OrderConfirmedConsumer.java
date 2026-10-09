package vn.t3nexus.inventory.infrastructure.adapter.messaging.order;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import vn.t3nexus.inventory.application.reservation.CommitReservation;
import vn.t3nexus.lib.events.EventEnvelopeDecoder;
import vn.t3nexus.lib.events.EventEnvelopeMdcPropagator;
import vn.t3nexus.lib.events.OutboxEventData;

/**
 * Idempotency: DB-based — {@code CommitReservation.handle()} tự no-op qua {@code isPending()}, cùng
 * pattern {@code OrderCancelledConsumer}. Đây là mảnh còn thiếu khiến {@code Reservation} trước đây
 * không có cách nào biết "đơn đã xong", phải nằm PENDING vĩnh viễn (xem V5 migration cũ).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderConfirmedConsumer {

    private final ObjectMapper objectMapper;
    private final EventEnvelopeDecoder decoder;
    private final CommitReservation commitReservation;

    @KafkaListener(
            topics  = "${app.kafka.topic.order-confirmed}",
            groupId = "${app.kafka.consumer-group.order}"
    )
    public void consume(String message) {
        OutboxEventData event = objectMapper.readValue(message, OutboxEventData.class);
        EventEnvelopeMdcPropagator.propagate(event.payload());

        try {
            Payload payload = decoder.decode(event, Payload.class);
            commitReservation.handle(new CommitReservation.Command(payload.orderId()));
        } catch (Exception e) {
            log.error("[OrderConfirmedConsumer] failed to process eventId={}", event.payload().eventId(), e);
            throw e;
        } finally {
            EventEnvelopeMdcPropagator.clear();
        }
    }

    private record Payload(String orderId) {}
}
