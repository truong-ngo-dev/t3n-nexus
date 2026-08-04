package vn.t3nexus.order.domain.order;

import vn.t3nexus.lib.common.domain.model.AbstractDomainEvent;

import java.time.Instant;
import java.util.UUID;

public class OrderCancelledEvent extends AbstractDomainEvent {

    private final OrderCancelReason reason;

    public OrderCancelledEvent(String orderId, OrderCancelReason reason) {
        super(UUID.randomUUID().toString(), Instant.now(), orderId, "Order");
        this.reason = reason;
    }

    @Override
    public String getRoutingKey() { return "order.order.cancelled"; }

    @Override
    public Object getPayload() { return new Payload(getAggregateId(), reason); }

    public OrderCancelReason reason() { return reason; }

    public record Payload(String orderId, OrderCancelReason reason) {}
}
