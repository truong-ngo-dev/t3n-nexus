package vn.t3nexus.lib.events;

import java.time.Instant;

/**
 * Kafka message contract cho mọi integration event giữa các bounded context.
 * Được build bởi OutboxEventStore và deserialize tại consumer infrastructure layer.
 * sourceService derivable từ topic prefix; schemaVersion added khi có nhu cầu thực tế.
 */
public record EventEnvelope(
        String eventId,
        String aggregateId,
        String aggregateType,
        String eventType,
        Instant occurredAt,
        String traceId,
        String spanId,
        Object payload
) {}
