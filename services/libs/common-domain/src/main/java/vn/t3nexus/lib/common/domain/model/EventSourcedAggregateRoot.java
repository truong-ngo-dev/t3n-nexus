package vn.t3nexus.lib.common.domain.model;

import java.util.List;

/**
 * Base for aggregate roots whose persisted state is a sequence of domain events,
 * not a snapshot of field values. In-memory state is always derived by replaying
 * events through {@link #apply(DomainEvent)} — this is the single path for state
 * change, whether triggered live (via {@link #raise}) or during rehydration
 * (via {@link #loadFromHistory}).
 *
 * <br>Not to be confused with the optimistic-lock {@code version} field on
 * {@link AbstractAggregateRoot} — that one backs JPA {@code @Version} for
 * state-based aggregates persisted as a single row. {@link #getRevision()} here
 * is the length of this aggregate's event stream, used as the expected version
 * when appending to an event store.
 *
 * @param <T> the type of the identifier
 */
public abstract class EventSourcedAggregateRoot<T extends Id<?>> extends AbstractAggregateRoot<T> {

    private long revision = 0;

    /**
     * Raise a new event: apply it to mutate state, record it for dispatch
     * (via {@link #addDomainEvent}), and advance the revision.
     * <br>Domain methods call this instead of assigning fields directly.
     */
    protected void raise(DomainEvent event) {
        apply(event);
        addDomainEvent(event);
        revision++;
    }

    /**
     * Mutate in-memory state for one event. Implementations typically dispatch
     * on event type (e.g. a {@code switch} over sealed event types).
     * <br>Must be side-effect-free besides field assignment — no validation,
     * no throwing. Validation belongs in the domain method that constructs the
     * event and calls {@link #raise}, before the event exists.
     */
    protected abstract void apply(DomainEvent event);

    /**
     * Rehydrate state by replaying persisted history, in order.
     * <br>Does NOT call {@link #addDomainEvent} — these events already happened
     * and were already published; replay must not re-trigger dispatch.
     */
    public final void loadFromHistory(List<DomainEvent> history) {
        for (DomainEvent event : history) {
            apply(event);
            revision++;
        }
    }

    /**
     * @return number of events applied so far — used as the expected revision
     *         when appending new events to an event store (optimistic concurrency).
     */
    public long getRevision() {
        return revision;
    }
}
