package vn.t3nexus.catalog.application.variant.event;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.t3nexus.catalog.domain.variant.VariantActivatedEvent;
import vn.t3nexus.lib.common.domain.service.EventHandler;
import vn.t3nexus.lib.outbox.OutboxEventStore;

@Component
@RequiredArgsConstructor
public class VariantActivatedHandler implements EventHandler<VariantActivatedEvent> {

    private final OutboxEventStore outboxEventStore;

    @Override
    public void handle(VariantActivatedEvent event) {
        outboxEventStore.store(event);
    }

    @Override
    public Class<VariantActivatedEvent> getEventType() {
        return VariantActivatedEvent.class;
    }
}
