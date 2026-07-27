package vn.t3nexus.lib.common.domain.service;

/**
 * Thrown by {@link EventStore#append} when {@code expectedRevision} no longer matches
 * the aggregate's true persisted revision — another writer appended concurrently.
 * Not a {@code DomainException}: this is an infrastructure-level concurrency conflict,
 * not a business rule violation. Callers should reload the aggregate and retry.
 */
public class EventStoreConflictException extends RuntimeException {

    private final String aggregateId;
    private final long expectedRevision;

    public EventStoreConflictException(String aggregateId, long expectedRevision) {
        super("Concurrent append conflict for aggregateId=" + aggregateId + ", expectedRevision=" + expectedRevision);
        this.aggregateId = aggregateId;
        this.expectedRevision = expectedRevision;
    }

    public String getAggregateId() { return aggregateId; }
    public long getExpectedRevision() { return expectedRevision; }
}
