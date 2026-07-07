package vn.t3nexus.inventory.application.reservation.event;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.t3nexus.inventory.domain.reservation.InventoryReservationFailedEvent;
import vn.t3nexus.lib.common.domain.service.EventHandler;
import vn.t3nexus.lib.outbox.OutboxEventStore;

@Component
@RequiredArgsConstructor
public class InventoryReservationFailedHandler implements EventHandler<InventoryReservationFailedEvent> {

    private final OutboxEventStore outboxEventStore;

    @Override
    public void handle(InventoryReservationFailedEvent event) {
        outboxEventStore.store(event);
    }

    @Override
    public Class<InventoryReservationFailedEvent> getEventType() {
        return InventoryReservationFailedEvent.class;
    }
}
