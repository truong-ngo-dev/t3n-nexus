package vn.t3nexus.catalog.domain.product;

import vn.t3nexus.lib.common.domain.model.AbstractDomainEvent;

import java.time.Instant;
import java.util.UUID;

public class ProductBlockedEvent extends AbstractDomainEvent {

    private final String sellerId;
    private final String reason;

    public ProductBlockedEvent(String productId, String sellerId, String reason) {
        super(UUID.randomUUID().toString(), Instant.now(), productId, "Product");
        this.sellerId = sellerId;
        this.reason   = reason;
    }

    @Override
    public String getRoutingKey() { return "catalog.product.blocked"; }

    @Override
    public Object getPayload() { return new Payload(getAggregateId(), sellerId, reason); }

    public String getSellerId() { return sellerId; }
    public String getReason()   { return reason; }

    public record Payload(String productId, String sellerId, String reason) {}
}
