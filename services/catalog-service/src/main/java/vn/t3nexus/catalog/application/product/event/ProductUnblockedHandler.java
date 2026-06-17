package vn.t3nexus.catalog.application.product.event;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.t3nexus.catalog.domain.product.ProductUnblockedEvent;
import vn.t3nexus.lib.common.domain.service.EventHandler;
import vn.t3nexus.lib.outbox.OutboxEventStore;

@Component
@RequiredArgsConstructor
public class ProductUnblockedHandler implements EventHandler<ProductUnblockedEvent> {

    private final OutboxEventStore outboxEventStore;

    @Override
    public void handle(ProductUnblockedEvent event) {
        outboxEventStore.store(event);
    }

    @Override
    public Class<ProductUnblockedEvent> getEventType() {
        return ProductUnblockedEvent.class;
    }
}
