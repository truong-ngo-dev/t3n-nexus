package vn.t3nexus.inventory.application.stock.event;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.t3nexus.inventory.domain.stock.StockDepletedEvent;
import vn.t3nexus.lib.common.domain.service.EventHandler;
import vn.t3nexus.lib.outbox.OutboxEventStore;

@Component
@RequiredArgsConstructor
public class StockDepletedHandler implements EventHandler<StockDepletedEvent> {

    private final OutboxEventStore outboxEventStore;

    @Override
    public void handle(StockDepletedEvent event) {
        outboxEventStore.store(event);
    }

    @Override
    public Class<StockDepletedEvent> getEventType() {
        return StockDepletedEvent.class;
    }
}
