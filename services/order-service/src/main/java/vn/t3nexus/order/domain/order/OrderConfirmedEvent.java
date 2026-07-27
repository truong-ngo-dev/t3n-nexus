package vn.t3nexus.order.domain.order;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import vn.t3nexus.lib.common.domain.model.AbstractDomainEvent;

import java.time.Instant;
import java.util.UUID;

public class OrderConfirmedEvent extends AbstractDomainEvent {

    public OrderConfirmedEvent(String orderId) {
        this(UUID.randomUUID().toString(), Instant.now(), orderId, "Order");
    }

    @JsonCreator
    public OrderConfirmedEvent(
            @JsonProperty("eventId") String eventId,
            @JsonProperty("occurredOn") Instant occurredOn,
            @JsonProperty("aggregateId") String aggregateId,
            @JsonProperty("aggregateType") String aggregateType) {
        super(eventId, occurredOn, aggregateId, aggregateType);
    }

    @Override
    public String getRoutingKey() { return "order.order.confirmed"; }

    @Override
    public Object getPayload() { return new Payload(getAggregateId()); }

    public record Payload(String orderId) {}
}
