package vn.t3nexus.lib.common.domain.service;

import vn.t3nexus.lib.common.domain.model.DomainEvent;

import java.util.List;

/**
 * Port for persisting and loading the event stream of an
 * {@link vn.t3nexus.lib.common.domain.model.EventSourcedAggregateRoot}.
 * Append-only — history is never mutated, only extended.
 */
public interface EventStore {

    /**
     * @return full event history for the aggregate, in append order.
     *         Empty list if the aggregate has never had any event (does not exist yet).
     */
    List<DomainEvent> loadEvents(String aggregateId);

    /**
     * Append new events as one atomic batch — all persist or none do.
     *
     * @param aggregateId      aggregate's id (string form)
     * @param aggregateType    e.g. "Order" — for querying/debugging, not identity
     * @param events           events to append, in order
     * @param expectedRevision revision the aggregate was at before these events were raised
     * @throws EventStoreConflictException if expectedRevision no longer matches the last
     *         persisted revision for this aggregate — another writer appended in the meantime;
     *         caller must reload and retry
     */
    void append(String aggregateId, String aggregateType, List<DomainEvent> events, long expectedRevision);
}
