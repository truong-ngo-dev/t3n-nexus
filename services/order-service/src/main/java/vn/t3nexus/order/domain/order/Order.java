package vn.t3nexus.order.domain.order;

import vn.t3nexus.lib.common.domain.model.AbstractAggregateRoot;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class Order extends AbstractAggregateRoot<OrderId> {

    private String customerId;
    private String sellerId;
    private List<OrderLineItem> items;
    private PaymentMethod paymentMethod;
    private ShippingAddress shippingAddress;
    private OrderStatus status;
    private OrderCancelReason cancelReason;
    private Instant createdAt;
    private Instant updatedAt;

    private Order() {}

    public static Order create(OrderId id, String customerId, String sellerId, List<OrderLineItem> items,
                               PaymentMethod paymentMethod, ShippingAddress shippingAddress) {
        if (items.isEmpty()) throw OrderException.emptyItems();
        if (shippingAddress == null) throw OrderException.missingShippingAddress();
        Instant now = Instant.now();
        Order order = new Order();
        order.setId(id);
        order.customerId = customerId;
        order.sellerId = sellerId;
        order.items = new ArrayList<>(items);
        order.paymentMethod = paymentMethod;
        order.shippingAddress = shippingAddress;
        order.status = OrderStatus.CREATED;
        order.createdAt = now;
        order.updatedAt = now;
        order.addDomainEvent(new OrderCreatedEvent(id.getValue(), customerId, sellerId, items, paymentMethod, shippingAddress));
        return order;
    }

    /** Reconstitute từ persistence — dùng bởi repository, không fire event. */
    public static Order reconstitute(OrderId id, String customerId, String sellerId, List<OrderLineItem> items,
                                     PaymentMethod paymentMethod, ShippingAddress shippingAddress,
                                     OrderStatus status, OrderCancelReason cancelReason,
                                     Instant createdAt, Instant updatedAt) {
        Order order = new Order();
        order.setId(id);
        order.customerId = customerId;
        order.sellerId = sellerId;
        order.items = new ArrayList<>(items);
        order.paymentMethod = paymentMethod;
        order.shippingAddress = shippingAddress;
        order.status = status;
        order.cancelReason = cancelReason;
        order.createdAt = createdAt;
        order.updatedAt = updatedAt;
        return order;
    }

    public void confirm() {
        if (status != OrderStatus.CREATED) throw OrderException.invalidTransition(status, "confirm");
        this.status = OrderStatus.CONFIRMED;
        this.updatedAt = Instant.now();
        addDomainEvent(new OrderConfirmedEvent(getId().getValue()));
    }

    public void cancel(OrderCancelReason reason) {
        if (status == OrderStatus.CANCELLED) return; // idempotent no-op
        if (status != OrderStatus.CREATED) throw OrderException.invalidTransition(status, "cancel");
        this.status = OrderStatus.CANCELLED;
        this.cancelReason = reason;
        this.updatedAt = Instant.now();
        addDomainEvent(new OrderCancelledEvent(getId().getValue(), reason));
    }

    /**
     * State machine check dùng bởi Saga consumer (InventoryReserved/InventoryReservationFailed) để
     * chặn late/duplicate reply — event chỉ hợp lệ khi order còn đang CREATED (chưa confirm/cancel).
     */
    public boolean canProcess() {
        return status == OrderStatus.CREATED;
    }

    public String getCustomerId() { return customerId; }
    public String getSellerId() { return sellerId; }
    public List<OrderLineItem> getItems() { return List.copyOf(items); }
    public PaymentMethod getPaymentMethod() { return paymentMethod; }
    public ShippingAddress getShippingAddress() { return shippingAddress; }
    public OrderStatus getStatus() { return status; }
    public OrderCancelReason getCancelReason() { return cancelReason; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
