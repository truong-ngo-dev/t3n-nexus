package vn.t3nexus.catalog.domain.product;

import vn.t3nexus.lib.common.domain.model.AbstractDomainEvent;

import java.time.Instant;
import java.util.UUID;

public class ProductUnblockedEvent extends AbstractDomainEvent {

    public ProductUnblockedEvent(String productId) {
        super(UUID.randomUUID().toString(), Instant.now(), productId, "Product");
    }

    @Override
    public String getRoutingKey() { return "catalog.product.unblocked"; }

    @Override
    public Object getPayload() { return new Payload(getAggregateId()); }

    public record Payload(String productId) {}
}
