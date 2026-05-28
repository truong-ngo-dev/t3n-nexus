package vn.t3nexus.lib.outbox;

import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import vn.t3nexus.lib.common.domain.model.DomainEvent;
import vn.t3nexus.lib.events.EventEnvelope;

import java.time.Instant;

@RequiredArgsConstructor
public class OutboxEventStore {

    private final OutboxEventRepository repository;
    private final ObjectMapper objectMapper;

    @Transactional
    public void store(DomainEvent event) {
        String  eventId    = event.getEventId();
        String  eventType  = event.getClass().getSimpleName();
        Instant occurredAt = event.getOccurredOn();
        String  traceId    = MDC.get("traceId");
        String  spanId     = MDC.get("spanId");
        try {
            EventEnvelope envelope = new EventEnvelope(
                    eventId,
                    event.getAggregateId(),
                    event.getAggregateType(),
                    eventType,
                    occurredAt,
                    traceId,
                    spanId,
                    event.getPayload()
            );
            String payload = objectMapper.writeValueAsString(envelope);
            repository.save(OutboxEvent.create(
                    eventId, event.getAggregateType(), event.getAggregateId(),
                    eventType, event.getRoutingKey(), payload, occurredAt, traceId, spanId
            ));
        } catch (JacksonException e) {
            throw new OutboxSerializationException(eventType, e);
        }
    }
}
