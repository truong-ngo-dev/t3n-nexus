package vn.t3nexus.order.application.order.event;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.t3nexus.lib.common.domain.service.EventHandler;
import vn.t3nexus.lib.outbox.OutboxEventStore;
import vn.t3nexus.order.domain.order.OrderCreatedEvent;

@Component
@RequiredArgsConstructor
public class OrderCreatedHandler implements EventHandler<OrderCreatedEvent> {

    private final OutboxEventStore outboxEventStore;

    @Override
    public void handle(OrderCreatedEvent event) {
        outboxEventStore.store(event);
    }

    @Override
    public Class<OrderCreatedEvent> getEventType() {
        return OrderCreatedEvent.class;
    }
}
