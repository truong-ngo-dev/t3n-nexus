package vn.t3nexus.catalog.application.product.event;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.t3nexus.catalog.domain.product.ProductUnpublishedEvent;
import vn.t3nexus.lib.common.domain.service.EventHandler;
import vn.t3nexus.lib.outbox.OutboxEventStore;

@Component
@RequiredArgsConstructor
public class ProductUnpublishedHandler implements EventHandler<ProductUnpublishedEvent> {

    private final OutboxEventStore outboxEventStore;

    @Override
    public void handle(ProductUnpublishedEvent event) {
        outboxEventStore.store(event);
    }

    @Override
    public Class<ProductUnpublishedEvent> getEventType() {
        return ProductUnpublishedEvent.class;
    }
}
