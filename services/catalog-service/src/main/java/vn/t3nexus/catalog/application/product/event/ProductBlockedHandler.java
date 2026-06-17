package vn.t3nexus.catalog.application.product.event;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.t3nexus.catalog.domain.product.ProductBlockedEvent;
import vn.t3nexus.lib.common.domain.service.EventHandler;
import vn.t3nexus.lib.outbox.OutboxEventStore;

@Component
@RequiredArgsConstructor
public class ProductBlockedHandler implements EventHandler<ProductBlockedEvent> {

    private final OutboxEventStore outboxEventStore;

    @Override
    public void handle(ProductBlockedEvent event) {
        outboxEventStore.store(event);
    }

    @Override
    public Class<ProductBlockedEvent> getEventType() {
        return ProductBlockedEvent.class;
    }
}
