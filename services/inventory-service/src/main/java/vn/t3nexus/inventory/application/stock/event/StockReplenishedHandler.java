package vn.t3nexus.inventory.application.stock.event;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.t3nexus.inventory.domain.stock.StockReplenishedEvent;
import vn.t3nexus.lib.common.domain.service.EventHandler;
import vn.t3nexus.lib.outbox.OutboxEventStore;

@Component
@RequiredArgsConstructor
public class StockReplenishedHandler implements EventHandler<StockReplenishedEvent> {

    private final OutboxEventStore outboxEventStore;

    @Override
    public void handle(StockReplenishedEvent event) {
        outboxEventStore.store(event);
    }

    @Override
    public Class<StockReplenishedEvent> getEventType() {
        return StockReplenishedEvent.class;
    }
}
