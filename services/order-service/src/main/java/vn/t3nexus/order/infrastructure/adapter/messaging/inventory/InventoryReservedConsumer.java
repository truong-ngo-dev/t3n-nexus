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
import vn.t3nexus.order.application.order.ConfirmOrderOnCodCreated;

/**
 * <p>Saga reply — decode payload rồi delegate toàn bộ quyết định nghiệp vụ (rẽ nhánh COD/PREPAID,
 * confirm order) cho {@link ConfirmOrderOnCodCreated}. Consumer chỉ giữ phần thuộc infra: decode
 * message + catch {@link OptimisticLockingFailureException} để chặn không cho lọt vào retry/DLQ của
 * Kafka &mdash; đây là quyết định "im lặng ack khi có race" thuộc về cách xử lý message, <b>không</b>
 * phải business rule, nên vẫn hợp lý giữ ở layer này.</p>
 *
 * <p>Idempotency: DB-based, <b>không</b> dùng Redis:</p>
 * <ul>
 *   <li>{@link ConfirmOrderOnCodCreated} tự no-op nếu order không còn ở <code>CREATED</code>
 *       (xem <code>Order.canProcess()</code>)</li>
 *   <li>{@link OptimisticLockingFailureException} (từ <code>@Version</code> trên bảng
 *       <code>orders</code>) bắt race concurrent update thật</li>
 * </ul>
 * <p>Không có "khoá" nào có thể rò rỉ nếu consumer crash giữa chừng, khác với Redis TTL key.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InventoryReservedConsumer {

    private final ObjectMapper objectMapper;
    private final EventEnvelopeDecoder decoder;
    private final ConfirmOrderOnCodCreated confirmOrderOnCodCreated;

    @KafkaListener(
            topics  = "${app.kafka.topic.inventory-reserved}",
            groupId = "${app.kafka.consumer-group.inventory}"
    )
    public void consume(String message) {
        OutboxEventData event = objectMapper.readValue(message, OutboxEventData.class);
        EventEnvelopeMdcPropagator.propagate(event.payload());
        Payload payload = decoder.decode(event, Payload.class);

        try {
            confirmOrderOnCodCreated.handle(new ConfirmOrderOnCodCreated.Command(payload.orderId()));
        } catch (OptimisticLockingFailureException e) {
            log.info("[InventoryReservedConsumer] concurrent update conflict, skip, orderId={}", payload.orderId());
        } finally {
            EventEnvelopeMdcPropagator.clear();
        }
    }

    private record Payload(String orderId) {}
}
