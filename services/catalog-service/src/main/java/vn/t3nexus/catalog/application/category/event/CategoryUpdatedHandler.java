package vn.t3nexus.catalog.application.category.event;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.t3nexus.catalog.domain.category.CategoryUpdatedEvent;
import vn.t3nexus.lib.common.domain.service.EventHandler;
import vn.t3nexus.lib.outbox.OutboxEventStore;

@Component
@RequiredArgsConstructor
public class CategoryUpdatedHandler implements EventHandler<CategoryUpdatedEvent> {

    private final OutboxEventStore outboxEventStore;

    @Override
    public void handle(CategoryUpdatedEvent event) {
        outboxEventStore.store(event);
    }

    @Override
    public Class<CategoryUpdatedEvent> getEventType() {
        return CategoryUpdatedEvent.class;
    }
}
