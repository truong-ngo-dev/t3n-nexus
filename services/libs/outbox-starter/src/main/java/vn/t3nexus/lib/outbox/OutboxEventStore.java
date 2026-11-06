package vn.t3nexus.lib.outbox;

import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import vn.t3nexus.lib.common.domain.model.DomainEvent;
import vn.t3nexus.lib.events.EventEnvelope;
import vn.t3nexus.lib.events.EventEnvelopeFactory;

import java.time.Instant;

/**
 * Cổng ghi hộp thư event. <b>Bắt buộc có giao dịch đang chạy</b> ({@link Propagation#MANDATORY}): hàng hộp thư phải
 * commit hoặc rollback cùng thay đổi dữ liệu của use case (R12). Gọi ngoài giao dịch là lỗi lập trình và bị từ chối
 * ngay, không tự mở giao dịch riêng.
 */
@RequiredArgsConstructor
public class OutboxEventStore {

    /** Phiên bản khung của event ghi qua cổng này; đổi nghĩa khung thì tăng. */
    static final int SCHEMA_VERSION = 1;

    private final OutboxEventRepository repository;
    private final EventEnvelopeFactory envelopeFactory;

    @Transactional(propagation = Propagation.MANDATORY)
    public void store(DomainEvent event) {
        String  eventId    = event.getEventId();
        String  eventType  = event.getClass().getSimpleName();
        Instant occurredAt = event.getOccurredOn();
        String  traceId    = MDC.get("traceId");
        String  spanId     = MDC.get("spanId");
        try {
            EventEnvelope envelope = envelopeFactory.create(
                    eventId,
                    event.getAggregateType(),
                    event.getAggregateId(),
                    eventType,
                    occurredAt,
                    traceId,
                    spanId,
                    SCHEMA_VERSION,
                    event.getPayload()
            );
            repository.save(OutboxEvent.create(
                    eventId, event.getAggregateType(), event.getAggregateId(),
                    eventType, event.getRoutingKey(), envelopeFactory.toJson(envelope), occurredAt, traceId, spanId
            ));
        } catch (JacksonException e) {
            throw new OutboxSerializationException(eventType, e);
        }
    }
}
