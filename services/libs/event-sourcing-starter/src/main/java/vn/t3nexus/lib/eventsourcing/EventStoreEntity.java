package vn.t3nexus.lib.eventsourcing;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * One row per event in an aggregate's history. Append-only — never updated, never deleted.
 * <br>{@code (aggregate_id, revision)} is the optimistic-concurrency guard: a concurrent
 * writer trying to append at a revision already taken fails on the unique constraint.
 */
@Entity
@Table(name = "event_store",
        uniqueConstraints = @UniqueConstraint(name = "uq_event_store_aggregate_revision", columnNames = {"aggregate_id", "revision"}),
        indexes = @Index(name = "idx_event_store_aggregate_id", columnList = "aggregate_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EventStoreEntity {

    /** Technical primary key */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Unique identifier of the aggregate instance */
    @Column(name = "aggregate_id", nullable = false, updatable = false)
    private String aggregateId;

    /** Type of the aggregate that produced the event (e.g., 'Order') */
    @Column(name = "aggregate_type", nullable = false, updatable = false)
    private String aggregateType;

    /** Position of this event in the aggregate's stream — 1-based, sequential, no gaps */
    @Column(nullable = false, updatable = false)
    private long revision;

    /**
     * Groups every event written by the same {@code EventStore.append()} call — i.e. every
     * event that resulted from the same use-case execution. Not derivable from
     * {@code revision} alone: two unrelated calls produce adjacent revisions too, so without
     * this column there is no reliable way to tell "same change" apart from "next change".
     */
    @Column(name = "correlation_id", nullable = false, updatable = false)
    private String correlationId;

    /** Logical event ID — same value used in the outbox for this event, if also dispatched */
    @Column(name = "event_id", nullable = false, updatable = false)
    private String eventId;

    /** Fully-qualified class name of the original DomainEvent — used to resolve the type on replay */
    @Column(name = "event_type", nullable = false, updatable = false)
    private String eventType;

    /** Full event serialized as JSON — includes base metadata (eventId, occurredOn, aggregateId) via getters */
    @Column(nullable = false, updatable = false, columnDefinition = "TEXT")
    private String payload;

    /** Timestamp when the event actually occurred in the domain */
    @Column(name = "occurred_on", nullable = false, updatable = false)
    private Instant occurredOn;

    /** Timestamp when the event was written to the event store */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static EventStoreEntity create(String aggregateId, String aggregateType, long revision,
                                          String correlationId, String eventId, String eventType,
                                          String payload, Instant occurredOn) {
        EventStoreEntity e = new EventStoreEntity();
        e.aggregateId = aggregateId;
        e.aggregateType = aggregateType;
        e.revision = revision;
        e.correlationId = correlationId;
        e.eventId = eventId;
        e.eventType = eventType;
        e.payload = payload;
        e.occurredOn = occurredOn;
        e.createdAt = Instant.now();
        return e;
    }
}
