package vn.t3nexus.lib.common.domain.model;

import java.util.ArrayList;
import java.util.Collection;

/**
 * Base implementation for aggregate roots.
 * <br>Manages domain events that occur during the aggregate's lifecycle.
 *
 * @param <T> the type of the identifier
 */
public abstract class AbstractAggregateRoot<T extends Id<?>> extends AbstractEntity<T> implements AggregateRoot<T> {

    private Collection<DomainEvent> domainEvents;

    @Override
    public Collection<DomainEvent> getDomainEvents() {
        return domainEvents;
    }

    @Override
    public void addDomainEvent(DomainEvent event) {
        if (domainEvents == null) domainEvents = new ArrayList<>();
        domainEvents.add(event);
    }

    @Override
    public void clearDomainEvents() {
        if (domainEvents == null) domainEvents = new ArrayList<>();
        domainEvents.clear();
    }
}
