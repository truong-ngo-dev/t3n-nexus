package vn.t3nexus.order.domain.order;

import vn.t3nexus.lib.common.domain.model.AbstractDomainEvent;

import java.time.Instant;
import java.util.UUID;

public class OrderConfirmedEvent extends AbstractDomainEvent {

    public OrderConfirmedEvent(String orderId) {
        super(UUID.randomUUID().toString(), Instant.now(), orderId, "Order");
    }

    @Override
    public String getRoutingKey() { return "order.order.confirmed"; }

    @Override
    public Object getPayload() { return new Payload(getAggregateId()); }

    public record Payload(String orderId) {}
}
