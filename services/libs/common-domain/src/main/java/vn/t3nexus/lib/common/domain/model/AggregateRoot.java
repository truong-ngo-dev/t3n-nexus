package vn.t3nexus.lib.common.domain.model;

import java.util.Collection;

/**
 * Represents the root of an aggregate, which is a cluster of associated objects that we treat as a unit for data changes.
 * <br>The aggregate root is responsible for maintaining consistency within the aggregate.
 *
 * @param <T> the type of the identifier
 */
public interface AggregateRoot<T extends Id<?>> extends Entity<T> {
    /**
     * @return a read-only collection of domain events that have occurred within this aggregate
     */
    Collection<DomainEvent> getDomainEvents();

    /**
     * Records a domain event that will be published when the aggregate is saved.
     *
     * @param event the domain event to add
     */
    void addDomainEvent(DomainEvent event);

    /**
     * Clears the recorded domain events.
     */
    void clearDomainEvents();
}
