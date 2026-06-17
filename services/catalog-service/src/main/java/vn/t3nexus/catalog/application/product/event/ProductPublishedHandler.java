package vn.t3nexus.catalog.application.product.event;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.t3nexus.catalog.domain.product.ProductPublishedEvent;
import vn.t3nexus.lib.common.domain.service.EventHandler;
import vn.t3nexus.lib.outbox.OutboxEventStore;

@Component
@RequiredArgsConstructor
public class ProductPublishedHandler implements EventHandler<ProductPublishedEvent> {

    private final OutboxEventStore outboxEventStore;

    @Override
    public void handle(ProductPublishedEvent event) {
        outboxEventStore.store(event);
    }

    @Override
    public Class<ProductPublishedEvent> getEventType() {
        return ProductPublishedEvent.class;
    }
}
