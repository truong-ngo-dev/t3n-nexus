package vn.t3nexus.lib.common.domain.model;

import java.time.Instant;

public abstract class AbstractDomainEvent implements DomainEvent {

    private final String eventId;
    private final Instant occurredOn;
    private final String aggregateId;
    private final String aggregateType;

    public AbstractDomainEvent(String eventId, Instant occurredOn, String aggregateId, String aggregateType) {
        this.eventId = eventId;
        this.occurredOn = occurredOn;
        this.aggregateId = aggregateId;
        this.aggregateType = aggregateType;
    }

    @Override
    public String getEventId() {
        return eventId;
    }

    @Override
    public Instant getOccurredOn() {
        return occurredOn;
    }

    @Override
    public String getAggregateId() {
        return aggregateId;
    }

    @Override
    public String getAggregateType() {
        return aggregateType;
    }
}
