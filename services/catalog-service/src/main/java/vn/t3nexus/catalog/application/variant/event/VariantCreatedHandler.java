package vn.t3nexus.catalog.application.variant.event;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.t3nexus.catalog.domain.variant.VariantCreatedEvent;
import vn.t3nexus.lib.common.domain.service.EventHandler;
import vn.t3nexus.lib.outbox.OutboxEventStore;

@Component
@RequiredArgsConstructor
public class VariantCreatedHandler implements EventHandler<VariantCreatedEvent> {

    private final OutboxEventStore outboxEventStore;

    @Override
    public void handle(VariantCreatedEvent event) {
        outboxEventStore.store(event);
    }

    @Override
    public Class<VariantCreatedEvent> getEventType() {
        return VariantCreatedEvent.class;
    }
}
