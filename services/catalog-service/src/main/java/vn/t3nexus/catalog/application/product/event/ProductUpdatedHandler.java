package vn.t3nexus.catalog.application.product.event;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.t3nexus.catalog.domain.product.ProductUpdatedEvent;
import vn.t3nexus.lib.common.domain.service.EventHandler;
import vn.t3nexus.lib.outbox.OutboxEventStore;

@Component
@RequiredArgsConstructor
public class ProductUpdatedHandler implements EventHandler<ProductUpdatedEvent> {

    private final OutboxEventStore outboxEventStore;

    @Override
    public void handle(ProductUpdatedEvent event) {
        outboxEventStore.store(event);
    }

    @Override
    public Class<ProductUpdatedEvent> getEventType() {
        return ProductUpdatedEvent.class;
    }
}
