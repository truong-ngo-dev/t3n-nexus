package vn.t3nexus.inventory.application.reservation.event;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.t3nexus.inventory.domain.reservation.InventoryReservedEvent;
import vn.t3nexus.lib.common.domain.service.EventHandler;
import vn.t3nexus.lib.outbox.OutboxEventStore;

@Component
@RequiredArgsConstructor
public class InventoryReservedHandler implements EventHandler<InventoryReservedEvent> {

    private final OutboxEventStore outboxEventStore;

    @Override
    public void handle(InventoryReservedEvent event) {
        outboxEventStore.store(event);
    }

    @Override
    public Class<InventoryReservedEvent> getEventType() {
        return InventoryReservedEvent.class;
    }
}
