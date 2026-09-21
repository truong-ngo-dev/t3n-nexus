package vn.t3nexus.order.infrastructure.adapter.messaging.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import vn.t3nexus.lib.events.EventEnvelopeDecoder;
import vn.t3nexus.lib.events.EventEnvelopeMdcPropagator;
import vn.t3nexus.lib.events.OutboxEventData;
import vn.t3nexus.order.application.order.ScanDbInventoryTimeout;
import vn.t3nexus.order.application.order.ScanRedisInventoryTimeout;

/**
 * Heartbeat từ {@code scheduler-service} — 2 taskType cho cơ chế {@code CREATED}-timeout (xem
 * {@code feature/07-place-order/implementation.md} Phase 5). Payload opaque, chỉ mang tín hiệu
 * "tới giờ quét rồi" — logic thật nằm trọn ở {@link ScanRedisInventoryTimeout}/{@link ScanDbInventoryTimeout}.
 *
 * <p><b>Tạm để rời khỏi Kafka</b> ({@code @KafkaListener} comment) — 2 {@code ScheduledJob} tương ứng
 * chưa được đăng ký/verify ở scheduler-service (Phase 5, "Lượt 1"). Bật lại annotation + khai báo
 * topic ở {@code application.properties} khi lượt 1 đã xác nhận job fire đúng nhịp.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScheduledJobFiredConsumer {

    private static final String TASK_TYPE_REDIS_SCAN = "ORDER_INVENTORY_TIMEOUT_REDIS_SCAN";
    private static final String TASK_TYPE_DB_SCAN = "ORDER_INVENTORY_TIMEOUT_DB_SCAN";

    private final ObjectMapper objectMapper;
    private final EventEnvelopeDecoder decoder;
    private final ScanRedisInventoryTimeout scanRedisInventoryTimeout;
    private final ScanDbInventoryTimeout scanDbInventoryTimeout;

    // @KafkaListener(
    //         topics  = "${app.kafka.topic.scheduler-job-fired}",
    //         groupId = "${app.kafka.consumer-group.scheduler}"
    // )
    public void consume(String message) {
        OutboxEventData event = objectMapper.readValue(message, OutboxEventData.class);
        EventEnvelopeMdcPropagator.propagate(event.payload());
        try {
            Payload payload = decoder.decode(event, Payload.class);
            switch (payload.taskType()) {
                case TASK_TYPE_REDIS_SCAN -> scanRedisInventoryTimeout.handle(new ScanRedisInventoryTimeout.Command());
                case TASK_TYPE_DB_SCAN -> scanDbInventoryTimeout.handle(new ScanDbInventoryTimeout.Command());
                default -> log.debug("[ScheduledJobFiredConsumer] taskType={} không thuộc order-service, skip", payload.taskType());
            }
        } finally {
            EventEnvelopeMdcPropagator.clear();
        }
    }

    private record Payload(String instanceId, String taskType) {}
}
