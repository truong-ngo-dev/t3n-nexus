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
import vn.t3nexus.order.application.order.ConfirmOrder;
import vn.t3nexus.order.domain.order.Order;
import vn.t3nexus.order.domain.order.OrderException;
import vn.t3nexus.order.domain.order.OrderId;
import vn.t3nexus.order.domain.order.OrderRepository;
import vn.t3nexus.order.domain.order.PaymentMethod;

/**
 * Saga reply — COD bỏ qua bước payment, đi thẳng CONFIRMED khi tồn kho đã reserve.
 * Idempotency: DB-based, không dùng Redis — {@code ConfirmOrder} tự no-op nếu order không còn
 * ở CREATED (xem {@code Order.canProcess()}), và {@code OptimisticLockingFailureException} (từ
 * {@code @Version} trên bảng {@code orders}) bắt race concurrent update thật — không có "khoá" nào
 * có thể rò rỉ nếu consumer crash giữa chừng, khác với Redis TTL key.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InventoryReservedConsumer {

    private final ObjectMapper objectMapper;
    private final EventEnvelopeDecoder decoder;
    private final OrderRepository orderRepository;
    private final ConfirmOrder confirmOrder;

    @KafkaListener(
            topics  = "${app.kafka.topic.inventory-reserved}",
            groupId = "${app.kafka.consumer-group.inventory}"
    )
    public void consume(String message) {
        OutboxEventData event = objectMapper.readValue(message, OutboxEventData.class);
        EventEnvelopeMdcPropagator.propagate(event.payload());
        Payload payload = decoder.decode(event, Payload.class);

        try {
            Order order = orderRepository.findById(OrderId.of(payload.orderId()))
                    .orElseThrow(OrderException::notFound);

            if (order.getPaymentMethod() != PaymentMethod.COD) {
                log.info("[InventoryReservedConsumer] paymentMethod={} (not COD), skip — handled by payment flow, orderId={}",
                        order.getPaymentMethod(), payload.orderId());
                return;
            }

            confirmOrder.handle(new ConfirmOrder.Command(payload.orderId()));
        } catch (OptimisticLockingFailureException e) {
            log.info("[InventoryReservedConsumer] concurrent update conflict, skip, orderId={}", payload.orderId());
        } finally {
            EventEnvelopeMdcPropagator.clear();
        }
    }

    private record Payload(String orderId) {}
}
