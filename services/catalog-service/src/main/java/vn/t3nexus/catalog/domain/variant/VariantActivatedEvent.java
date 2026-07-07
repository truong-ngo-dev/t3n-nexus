package vn.t3nexus.catalog.domain.variant;

import vn.t3nexus.lib.common.domain.model.AbstractDomainEvent;

import java.time.Instant;
import java.util.UUID;

public class VariantActivatedEvent extends AbstractDomainEvent {

    private final String productId;

    public VariantActivatedEvent(String skuId, String productId) {
        super(UUID.randomUUID().toString(), Instant.now(), skuId, "Variant");
        this.productId = productId;
    }

    @Override
    public String getRoutingKey() { return "catalog.variant.activated"; }

    @Override
    public Object getPayload() { return new Payload(getAggregateId(), productId); }

    public record Payload(String skuId, String productId) {}
}
