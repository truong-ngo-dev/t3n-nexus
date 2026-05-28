package vn.t3nexus.lib.common.domain.model;

import java.time.Instant;

/**
 * Represents something that happened in the domain that domain experts care about.
 * <br>Domain events are immutable and should be named in the past tense.
 */
public interface DomainEvent {
    String getEventId();
    Instant getOccurredOn();
    String getAggregateId();
    String getAggregateType();

    /** Kafka topic this event routes to, e.g. {@code "identity.user.registered"}. */
    String getRoutingKey();

    /** Business-specific fields only — no base-class metadata. */
    Object getPayload();
}
