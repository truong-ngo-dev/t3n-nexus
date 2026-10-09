package vn.t3nexus.notification.infrastructure.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import vn.t3nexus.lib.events.EventEnvelope;
import vn.t3nexus.lib.events.EventEnvelopeMdcPropagator;
import vn.t3nexus.lib.events.OutboxEventData;
import vn.t3nexus.notification.application.handler.OrderCancelledPayload;
import vn.t3nexus.notification.application.handler.OrderConfirmedPayload;
import vn.t3nexus.notification.application.handler.SendOrderCancelledEmail;
import vn.t3nexus.notification.application.handler.SendOrderConfirmedEmail;
import vn.t3nexus.notification.application.notification.NotificationTrigger;

/**
 * Kênh Email cho `OrderConfirmed`/`OrderCancelled` — consumer group RIÊNG
 * ({@code app.kafka.consumer-group.order-email}), tách khỏi pool Tier1 chung
 * ({@code NotificationOutboxEventConsumer}, dùng cho OTP/verification) để tránh burst order-volume
 * gây priority-inversion (xem service.md §Channel Routing "Open question").
 *
 * <p>Cố tình không đi qua {@code NotificationDispatchService}/{@code NotificationHandlerRegistry} —
 * abstraction đó chỉ hỗ trợ 1 handler/eventType dùng chung 1 consumer, không khớp nhu cầu "cùng event,
 * 2 kênh, 2 consumer group độc lập" ở đây.</p>
 *
 * <p><b>Tạm để rời khỏi Kafka</b> ({@code @KafkaListener} comment) — kích hoạt khi đã quyết xong phần
 * rate-limit/backpressure cho Email volume cao (xem "Open question" ở trên) và có nguồn lấy email
 * seller cho {@code OrderConfirmed} (hiện chỉ gửi được customer, xem TODO trong
 * {@link SendOrderConfirmedEmail}).</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEmailOutboxEventConsumer {

    private final ObjectMapper objectMapper;
    private final SendOrderConfirmedEmail sendOrderConfirmedEmail;
    private final SendOrderCancelledEmail sendOrderCancelledEmail;

    // @KafkaListener(
    //         topics  = {"${app.kafka.topic.order-confirmed}", "${app.kafka.topic.order-cancelled}"},
    //         groupId = "${app.kafka.consumer-group.order-email}"
    // )
    public void consume(String message) {
        OutboxEventData event = objectMapper.readValue(message, OutboxEventData.class);
        EventEnvelope envelope = event.payload();
        EventEnvelopeMdcPropagator.propagate(envelope);
        try {
            NotificationTrigger trigger = NotificationTrigger.from(envelope);
            switch (trigger.eventType()) {
                case "OrderConfirmedEvent" -> sendOrderConfirmedEmail.handle(
                        trigger, objectMapper.convertValue(trigger.payload(), OrderConfirmedPayload.class));
                case "OrderCancelledEvent" -> sendOrderCancelledEmail.handle(
                        trigger, objectMapper.convertValue(trigger.payload(), OrderCancelledPayload.class));
                default -> log.debug("[OrderEmailOutboxEventConsumer] eventType={} không thuộc consumer này, skip", trigger.eventType());
            }
        } finally {
            EventEnvelopeMdcPropagator.clear();
        }
    }
}
