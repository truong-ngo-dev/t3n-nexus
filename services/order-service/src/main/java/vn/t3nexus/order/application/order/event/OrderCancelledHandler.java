package vn.t3nexus.order.application.order.event;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.t3nexus.lib.common.domain.service.EventHandler;
import vn.t3nexus.lib.outbox.OutboxEventStore;
import vn.t3nexus.order.domain.order.OrderCancelledEvent;

@Component
@RequiredArgsConstructor
public class OrderCancelledHandler implements EventHandler<OrderCancelledEvent> {

    private final OutboxEventStore outboxEventStore;

    @Override
    public void handle(OrderCancelledEvent event) {
        outboxEventStore.store(event);
    }

    @Override
    public Class<OrderCancelledEvent> getEventType() {
        return OrderCancelledEvent.class;
    }
}
