package vn.t3nexus.order.infrastructure.adapter.messaging.inventory;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import vn.t3nexus.lib.events.EventEnvelopeDecoder;
import vn.t3nexus.lib.events.EventEnvelopeMdcPropagator;
import vn.t3nexus.lib.events.OutboxEventData;
import vn.t3nexus.order.application.order.CancelOrder;
import vn.t3nexus.order.domain.order.OrderCancelReason;

/**
 * Saga compensate — tồn kho hết ngay sau khi tạo Order (cả COD lẫn Prepaid).
 * Idempotency: xem ghi chú ở {@link InventoryReservedConsumer}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InventoryReservationFailedConsumer {

    private final ObjectMapper objectMapper;
    private final EventEnvelopeDecoder decoder;
    private final CancelOrder cancelOrder;

    @KafkaListener(
            topics  = "${app.kafka.topic.inventory-reservation-failed}",
            groupId = "${app.kafka.consumer-group.inventory}"
    )
    public void consume(String message) {
        OutboxEventData event = objectMapper.readValue(message, OutboxEventData.class);
        EventEnvelopeMdcPropagator.propagate(event.payload());
        Payload payload = decoder.decode(event, Payload.class);

        try {
            cancelOrder.handle(new CancelOrder.Command(payload.orderId(), OrderCancelReason.OUT_OF_STOCK));
        } catch (OptimisticLockingFailureException e) {
            log.info("[InventoryReservationFailedConsumer] concurrent update conflict, skip, orderId={}", payload.orderId());
        } finally {
            EventEnvelopeMdcPropagator.clear();
        }
    }

    private record Payload(String orderId, String failedSkuId, String reason) {}
}
