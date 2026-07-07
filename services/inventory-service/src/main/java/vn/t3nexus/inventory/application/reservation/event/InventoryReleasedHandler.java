package vn.t3nexus.inventory.application.reservation.event;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.t3nexus.inventory.domain.reservation.InventoryReleasedEvent;
import vn.t3nexus.lib.common.domain.service.EventHandler;
import vn.t3nexus.lib.outbox.OutboxEventStore;

@Component
@RequiredArgsConstructor
public class InventoryReleasedHandler implements EventHandler<InventoryReleasedEvent> {

    private final OutboxEventStore outboxEventStore;

    @Override
    public void handle(InventoryReleasedEvent event) {
        outboxEventStore.store(event);
    }

    @Override
    public Class<InventoryReleasedEvent> getEventType() {
        return InventoryReleasedEvent.class;
    }
}
