package vn.t3nexus.lib.outbox;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Represents a domain event persisted in the outbox table.
 * <br>Debezium (or another CDC tool) monitors this table to publish events to Kafka.
 */
@Entity
@Table(name = "outbox_events", indexes = {
        @Index(name = "idx_outbox_created_at", columnList = "created_at")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboxEvent {

    /** Technical primary key */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Logical event ID — used by Debezium EventRouter for idempotent publishing */
    @Column(name = "event_id", nullable = false, updatable = false)
    private String eventId;

    /** Type of the aggregate that produced the event (e.g., 'Order', 'Customer') */
    @Column(nullable = false)
    private String aggregateType;

    /** Unique identifier of the aggregate instance */
    @Column(nullable = false)
    private String aggregateId;

    /** The name of the event class */
    @Column(nullable = false)
    private String eventType;

    /** Kafka topic this event routes to — used by Debezium EventRouter */
    @Column(name = "routing_key", nullable = false)
    private String routingKey;

    /** JSON representation of the event data */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    /** Timestamp when the event actually occurred in the domain */
    @Column(nullable = false)
    private Instant occurredOn;

    /** Timestamp when the event was written to the outbox */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** OTel trace ID captured from MDC at write time — propagated into EventEnvelope by Debezium route */
    @Column(name = "trace_id")
    private String traceId;

    /** OTel span ID captured from MDC at write time — used as remote parent when integrating OTel at consumer */
    @Column(name = "span_id")
    private String spanId;

    /**
     * Creates a new outbox event ready for persistence.
     */
    public static OutboxEvent create(String eventId, String aggregateType, String aggregateId,
                                     String eventType, String routingKey, String payload,
                                     Instant occurredOn, String traceId, String spanId) {
        OutboxEvent e = new OutboxEvent();
        e.eventId = eventId;
        e.aggregateType = aggregateType;
        e.aggregateId = aggregateId;
        e.eventType = eventType;
        e.routingKey = routingKey;
        e.payload = payload;
        e.occurredOn = occurredOn;
        e.createdAt = Instant.now();
        e.traceId = traceId;
        e.spanId = spanId;
        return e;
    }
}
