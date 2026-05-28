package vn.t3nexus.lib.common.application;

import vn.t3nexus.lib.common.domain.model.DomainEvent;
import vn.t3nexus.lib.common.domain.service.EventHandler;

import java.util.*;
import java.util.stream.Collectors;

/**
 * In-process domain event dispatcher.
 *
 * <p><b>Transaction contract:</b> dispatchAll() must be called within an active transaction.
 * All handlers participate in the caller's transaction. One handler throwing causes
 * the exception to propagate and the transaction to roll back (fail-fast).
 *
 * <p><b>Thread safety:</b> registry is immutable after construction — safe for concurrent reads.
 */
public class EventDispatcher {

    private final Map<Class<?>, List<EventHandler<?>>> registry;

    public EventDispatcher(List<EventHandler<?>> handlers) {
        Map<Class<?>, List<EventHandler<?>>> map = new HashMap<>();
        for (EventHandler<?> handler : handlers) {
            map.computeIfAbsent(handler.getEventType(), k -> new ArrayList<>())
               .add(handler);
        }
        map.values().forEach(list ->
                list.sort(Comparator.comparingInt(EventHandler::getOrder)));
        this.registry = map.entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(
                        Map.Entry::getKey,
                        e -> List.copyOf(e.getValue())));
    }

    @SuppressWarnings("unchecked")
    public void dispatch(DomainEvent event) {
        registry.getOrDefault(event.getClass(), List.of())
                .forEach(handler -> ((EventHandler<DomainEvent>) handler).handle(event));
    }

    public void dispatchAll(Collection<DomainEvent> events) {
        events.forEach(this::dispatch);
    }
}
