package vn.t3nexus.inventory.infrastructure.adapter.messaging.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import vn.t3nexus.inventory.application.reservation.ScanDbReservationTimeout;
import vn.t3nexus.inventory.application.reservation.ScanRedisReservationTimeout;
import vn.t3nexus.lib.events.EventEnvelopeDecoder;
import vn.t3nexus.lib.events.EventEnvelopeMdcPropagator;
import vn.t3nexus.lib.events.OutboxEventData;

/**
 * Heartbeat từ {@code scheduler-service} — 2 taskType cho TTL self-guard của {@code Reservation}.
 * Payload opaque, chỉ mang tín hiệu "tới giờ quét rồi" — logic thật nằm trọn ở
 * {@link ScanRedisReservationTimeout}/{@link ScanDbReservationTimeout}.
 *
 * <p><b>Tạm để rời khỏi Kafka</b> ({@code @KafkaListener} comment) — 2 {@code ScheduledJob} tương ứng
 * chưa được đăng ký/verify ở scheduler-service. Bật lại annotation + khai báo topic ở
 * {@code application.properties} khi đã đăng ký xong (cùng cơ chế đã áp dụng cho order-service).</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScheduledJobFiredConsumer {

    private static final String TASK_TYPE_REDIS_SCAN = "RESERVATION_TIMEOUT_REDIS_SCAN";
    private static final String TASK_TYPE_DB_SCAN = "RESERVATION_TIMEOUT_DB_SCAN";

    private final ObjectMapper objectMapper;
    private final EventEnvelopeDecoder decoder;
    private final ScanRedisReservationTimeout scanRedisReservationTimeout;
    private final ScanDbReservationTimeout scanDbReservationTimeout;

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
                case TASK_TYPE_REDIS_SCAN -> scanRedisReservationTimeout.handle(new ScanRedisReservationTimeout.Command());
                case TASK_TYPE_DB_SCAN -> scanDbReservationTimeout.handle(new ScanDbReservationTimeout.Command());
                default -> log.debug("[ScheduledJobFiredConsumer] taskType={} không thuộc inventory-service, skip", payload.taskType());
            }
        } finally {
            EventEnvelopeMdcPropagator.clear();
        }
    }

    private record Payload(String instanceId, String taskType) {}
}
