package vn.t3nexus.order.domain.order;

import vn.t3nexus.lib.common.domain.model.AbstractDomainEvent;

import java.time.Instant;
import java.util.UUID;

public class OrderConfirmedEvent extends AbstractDomainEvent {

    private final String customerId;
    private final String sellerId;
    private final ShippingAddress shippingAddress;

    public OrderConfirmedEvent(String orderId, String customerId, String sellerId, ShippingAddress shippingAddress) {
        super(UUID.randomUUID().toString(), Instant.now(), orderId, "Order");
        this.customerId = customerId;
        this.sellerId = sellerId;
        this.shippingAddress = shippingAddress;
    }

    @Override
    public String getRoutingKey() { return "order.order.confirmed"; }

    @Override
    public Object getPayload() {
        return new Payload(getAggregateId(), customerId, sellerId, shippingAddress);
    }

    public record Payload(String orderId, String customerId, String sellerId, ShippingAddress shippingAddress) {}
}
