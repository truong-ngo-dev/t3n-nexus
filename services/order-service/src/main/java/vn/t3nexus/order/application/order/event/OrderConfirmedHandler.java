package vn.t3nexus.order.application.order.event;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.t3nexus.lib.common.domain.service.EventHandler;
import vn.t3nexus.lib.outbox.OutboxEventStore;
import vn.t3nexus.order.domain.order.OrderConfirmedEvent;

@Component
@RequiredArgsConstructor
public class OrderConfirmedHandler implements EventHandler<OrderConfirmedEvent> {

    private final OutboxEventStore outboxEventStore;

    @Override
    public void handle(OrderConfirmedEvent event) {
        outboxEventStore.store(event);
    }

    @Override
    public Class<OrderConfirmedEvent> getEventType() {
        return OrderConfirmedEvent.class;
    }
}
