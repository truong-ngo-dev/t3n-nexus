package vn.t3nexus.order.domain.order;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import vn.t3nexus.lib.common.domain.model.AbstractDomainEvent;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class OrderCreatedEvent extends AbstractDomainEvent {

    private final String customerId;
    private final String sellerId;
    private final List<OrderLineItem> items;

    /** Business constructor — dùng khi raise() lúc hành động nghiệp vụ xảy ra thật. */
    public OrderCreatedEvent(String orderId, String customerId, String sellerId, List<OrderLineItem> items) {
        this(UUID.randomUUID().toString(), Instant.now(), orderId, "Order", customerId, sellerId, items);
    }

    /**
     * Reconstitution constructor — dùng bởi {@code JpaEventStore} khi replay từ event_store.
     * Nhận thẳng metadata gốc thay vì tự sinh mới, để không làm sai lệch eventId/occurredOn lịch sử.
     */
    @JsonCreator
    public OrderCreatedEvent(
            @JsonProperty("eventId") String eventId,
            @JsonProperty("occurredOn") Instant occurredOn,
            @JsonProperty("aggregateId") String aggregateId,
            @JsonProperty("aggregateType") String aggregateType,
            @JsonProperty("payload") Payload payload) {
        super(eventId, occurredOn, aggregateId, aggregateType);
        this.customerId = payload.customerId();
        this.sellerId = payload.sellerId();
        this.items = payload.items();
    }

    private OrderCreatedEvent(String eventId, Instant occurredOn, String aggregateId, String aggregateType,
                              String customerId, String sellerId, List<OrderLineItem> items) {
        super(eventId, occurredOn, aggregateId, aggregateType);
        this.customerId = customerId;
        this.sellerId = sellerId;
        this.items = items;
    }

    @Override
    public String getRoutingKey() { return "order.order.created"; }

    @Override
    public Object getPayload() { return new Payload(customerId, sellerId, items); }

    public String customerId() { return customerId; }
    public String sellerId() { return sellerId; }
    public List<OrderLineItem> items() { return items; }

    public record Payload(String customerId, String sellerId, List<OrderLineItem> items) {}
}
