package vn.t3nexus.catalog.application.variant.event;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.t3nexus.catalog.domain.variant.VariantPriceChangedEvent;
import vn.t3nexus.lib.common.domain.service.EventHandler;
import vn.t3nexus.lib.outbox.OutboxEventStore;

@Component
@RequiredArgsConstructor
public class VariantPriceChangedHandler implements EventHandler<VariantPriceChangedEvent> {

    private final OutboxEventStore outboxEventStore;

    @Override
    public void handle(VariantPriceChangedEvent event) {
        outboxEventStore.store(event);
    }

    @Override
    public Class<VariantPriceChangedEvent> getEventType() {
        return VariantPriceChangedEvent.class;
    }
}
