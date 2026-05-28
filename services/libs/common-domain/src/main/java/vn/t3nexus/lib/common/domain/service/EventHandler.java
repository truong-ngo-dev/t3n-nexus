package vn.t3nexus.lib.common.domain.service;

import vn.t3nexus.lib.common.domain.model.DomainEvent;

public interface EventHandler<E extends DomainEvent> {

    void handle(E event);

    Class<E> getEventType();

    default int getOrder() {
        return 100;
    }
}
