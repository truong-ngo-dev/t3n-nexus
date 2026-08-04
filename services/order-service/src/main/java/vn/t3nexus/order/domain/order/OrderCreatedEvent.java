package vn.t3nexus.order.domain.order;

import vn.t3nexus.lib.common.domain.model.AbstractDomainEvent;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class OrderCreatedEvent extends AbstractDomainEvent {

    private final String customerId;
    private final String sellerId;
    private final List<OrderLineItem> items;
    private final PaymentMethod paymentMethod;
    private final ShippingAddress shippingAddress;

    public OrderCreatedEvent(String orderId, String customerId, String sellerId, List<OrderLineItem> items,
                             PaymentMethod paymentMethod, ShippingAddress shippingAddress) {
        super(UUID.randomUUID().toString(), Instant.now(), orderId, "Order");
        this.customerId = customerId;
        this.sellerId = sellerId;
        this.items = items;
        this.paymentMethod = paymentMethod;
        this.shippingAddress = shippingAddress;
    }

    @Override
    public String getRoutingKey() { return "order.order.created"; }

    @Override
    public Object getPayload() { return new Payload(customerId, sellerId, items, paymentMethod, shippingAddress); }

    public String customerId() { return customerId; }
    public String sellerId() { return sellerId; }
    public List<OrderLineItem> items() { return items; }
    public PaymentMethod paymentMethod() { return paymentMethod; }
    public ShippingAddress shippingAddress() { return shippingAddress; }

    public record Payload(String customerId, String sellerId, List<OrderLineItem> items,
                          PaymentMethod paymentMethod, ShippingAddress shippingAddress) {}
}
