package vn.t3nexus.order.domain.order;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import vn.t3nexus.lib.common.domain.model.AbstractDomainEvent;

import java.time.Instant;
import java.util.UUID;

public class OrderCancelledEvent extends AbstractDomainEvent {

    private final String reason;

    public OrderCancelledEvent(String orderId, String reason) {
        this(UUID.randomUUID().toString(), Instant.now(), orderId, "Order", reason);
    }

    @JsonCreator
    public OrderCancelledEvent(
            @JsonProperty("eventId") String eventId,
            @JsonProperty("occurredOn") Instant occurredOn,
            @JsonProperty("aggregateId") String aggregateId,
            @JsonProperty("aggregateType") String aggregateType,
            @JsonProperty("payload") Payload payload) {
        super(eventId, occurredOn, aggregateId, aggregateType);
        this.reason = payload.reason();
    }

    private OrderCancelledEvent(String eventId, Instant occurredOn, String aggregateId, String aggregateType, String reason) {
        super(eventId, occurredOn, aggregateId, aggregateType);
        this.reason = reason;
    }

    @Override
    public String getRoutingKey() { return "order.order.cancelled"; }

    @Override
    public Object getPayload() { return new Payload(reason); }

    public String reason() { return reason; }

    public record Payload(String reason) {}
}
