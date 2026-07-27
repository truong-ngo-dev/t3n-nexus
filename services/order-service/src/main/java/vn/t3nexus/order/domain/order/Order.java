package vn.t3nexus.order.domain.order;

import vn.t3nexus.lib.common.domain.model.DomainEvent;
import vn.t3nexus.lib.common.domain.model.EventSourcedAggregateRoot;

import java.util.List;

public class Order extends EventSourcedAggregateRoot<OrderId> {

    private String customerId;
    private String sellerId;
    private List<OrderLineItem> items;
    private OrderStatus status;
    private String cancelReason;

    public static Order create(OrderId id, String customerId, String sellerId, List<OrderLineItem> items) {
        if (items.isEmpty()) throw OrderException.emptyItems();
        Order order = new Order();
        order.raise(new OrderCreatedEvent(id.getValue(), customerId, sellerId, items));
        return order;
    }

    /** Rehydrate từ event_store — history không được rỗng, caller (repository) tự lo trường hợp not-found. */
    public static Order rehydrate(List<DomainEvent> history) {
        Order order = new Order();
        order.loadFromHistory(history);
        return order;
    }

    public void confirm() {
        if (status != OrderStatus.CREATED) throw OrderException.invalidTransition(status, "confirm");
        raise(new OrderConfirmedEvent(getId().getValue()));
    }

    public void cancel(String reason) {
        if (status == OrderStatus.CANCELLED) return; // idempotent no-op
        if (status != OrderStatus.CREATED) throw OrderException.invalidTransition(status, "cancel");
        raise(new OrderCancelledEvent(getId().getValue(), reason));
    }

    /**
     * State machine check dùng bởi Saga consumer sau này (InventoryReserved/InventoryReservationFailed...)
     * để chặn late reply — event chỉ hợp lệ khi order còn đang CREATED (chưa confirm/cancel).
     */
    public boolean canProcess(Class<? extends DomainEvent> incoming) {
        return status == OrderStatus.CREATED;
    }

    @Override
    protected void apply(DomainEvent event) {
        switch (event) {
            case OrderCreatedEvent e -> {
                setId(OrderId.of(e.getAggregateId()));
                this.customerId = e.customerId();
                this.sellerId = e.sellerId();
                this.items = e.items();
                this.status = OrderStatus.CREATED;
            }
            case OrderConfirmedEvent ignored -> this.status = OrderStatus.CONFIRMED;
            case OrderCancelledEvent e -> {
                this.status = OrderStatus.CANCELLED;
                this.cancelReason = e.reason();
            }
            default -> throw new IllegalStateException("Unhandled event for Order: " + event.getClass());
        }
    }

    public String getCustomerId() { return customerId; }
    public String getSellerId() { return sellerId; }
    public List<OrderLineItem> getItems() { return List.copyOf(items); }
    public OrderStatus getStatus() { return status; }
    public String getCancelReason() { return cancelReason; }
}
