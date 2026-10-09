package vn.t3nexus.scheduler.application.scheduled_job_instance.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import vn.t3nexus.lib.common.domain.service.EventHandler;
import vn.t3nexus.lib.outbox.OutboxEventStore;
import vn.t3nexus.scheduler.domain.scheduled_job_instance.ScheduledJobFiredEvent;

/**
 * <b>TẠM THỜI chỉ log — chưa ghi Outbox.</b> Mục đích: verify luồng
 * {@code IndexPoller → FireScheduledJob → ScheduledJobFireService.fire() →
 * ScheduledJobInstance.dispatch() → save() → EventDispatcher.dispatchAll()} thực sự chạy tới đây, trước
 * khi wire publish thật.
 *
 * <p>Trước khi có class này, {@code ScheduledJobFiredEvent} raise xong <b>rơi vào hư không</b>:
 * {@code EventDispatcherConfig} nhận {@code List<EventHandler<?>>} rỗng (chưa handler nào đăng ký, xem
 * {@code implementation.md} Phase 2) → {@code dispatchAll()} không gọi ai, aggregate chỉ tự
 * {@code clearDomainEvents()}. Đăng ký tự động — chỉ cần {@code @Component implements EventHandler<...>},
 * Spring gom vào {@code List<EventHandler<?>>} cho {@code EventDispatcherConfig}, không cần khai báo gì
 * thêm.
 *
 * <p><b>KHÔNG phải điểm publish Kafka thật.</b> Event chỉ thực sự rời process qua Outbox + Debezium CDC
 * (Outbox Pattern, ADR-005) — {@code log.info} ở đây không thay thế bước đó.
 *
 * <p><b>2026-09-22</b>: đã inject sẵn {@code outboxEventStore}, thân {@code handle()} đã viết đúng dạng
 * {@code ProductPublishedHandler} (catalog-service) bên dưới — nhưng lệnh gọi thật vẫn đang
 * <b>comment</b>, chỉ log, cho tới khi test với đủ component (2 {@code ScheduledJob} của order-service
 * đăng ký + verify fire đúng nhịp, và {@code order-service.ScheduledJobFiredConsumer} sẵn sàng bật
 * {@code @KafkaListener} — xem {@code feature/07-place-order/implementation.md} Phase 5). Bỏ comment
 * dòng {@code outboxEventStore.store(event)} (và có thể bỏ luôn {@code log.info}) khi tới lúc.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScheduledJobFiredHandler implements EventHandler<ScheduledJobFiredEvent> {

    private final OutboxEventStore outboxEventStore;

    @Override
    public void handle(ScheduledJobFiredEvent event) {
        log.info("[ScheduledJobFiredHandler] fired (log-only, chưa outbox): instanceId={}, scheduledJobId={}, " +
                        "taskType={}, payload={}, traceId={}",
                event.instanceId(), event.scheduledJobId(), event.taskType(), event.payload(), MDC.get("traceId"));
        // outboxEventStore.store(event);
    }

    @Override
    public Class<ScheduledJobFiredEvent> getEventType() {
        return ScheduledJobFiredEvent.class;
    }
}
