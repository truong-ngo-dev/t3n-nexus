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

import java.util.List;

/**
 * Idempotency: DB-based, không dùng Redis — cùng lý do đã áp dụng cho order-service
 * (xem {@code payment-checkout/implementation.md} Phase 3): Redis {@code tryAcquire}/{@code release}
 * có lỗ hổng thật nếu consumer crash giữa 2 bước đó (key rò rỉ, event bị nuốt mất khi Kafka redeliver).
 * {@code ReserveInventory.handle()} tự idempotent qua {@code existsByOrderId()} (fast-path) +
 * {@code UNIQUE(order_id)} + catch {@code DataIntegrityViolationException} (race thật) —
 * {@code RecordReservationFailure.handle()} cùng cơ chế UNIQUE constraint. Không có "khoá" nào
 * có thể rò rỉ.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderCreatedConsumer {

    private final ObjectMapper objectMapper;
    private final EventEnvelopeDecoder decoder;
    private final ReserveInventory reserveInventory;
    private final RecordReservationFailure recordReservationFailure;

    @KafkaListener(
            topics  = "${app.kafka.topic.order-created}",
            groupId = "${app.kafka.consumer-group.order}"
    )
    public void consume(String message) {
        OutboxEventData event = objectMapper.readValue(message, OutboxEventData.class);
        EventEnvelopeMdcPropagator.propagate(event.payload());

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
