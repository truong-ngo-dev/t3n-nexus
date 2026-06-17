package vn.t3nexus.catalog.application.variant.event;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.t3nexus.catalog.domain.variant.VariantDeactivatedEvent;
import vn.t3nexus.lib.common.domain.service.EventHandler;
import vn.t3nexus.lib.outbox.OutboxEventStore;

@Component
@RequiredArgsConstructor
public class VariantDeactivatedHandler implements EventHandler<VariantDeactivatedEvent> {

    private final OutboxEventStore outboxEventStore;

    @Override
    public void handle(VariantDeactivatedEvent event) {
        outboxEventStore.store(event);
    }

    @Override
    public Class<VariantDeactivatedEvent> getEventType() {
        return VariantDeactivatedEvent.class;
    }
}
