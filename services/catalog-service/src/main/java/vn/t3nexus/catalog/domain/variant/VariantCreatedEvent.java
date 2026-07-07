package vn.t3nexus.catalog.domain.variant;

import vn.t3nexus.lib.common.domain.model.AbstractDomainEvent;

import java.time.Instant;
import java.util.UUID;

public class VariantCreatedEvent extends AbstractDomainEvent {

    private final String productId;
    private final String sellerId;
    private final boolean active;
    private final boolean productPublished;

    public VariantCreatedEvent(String skuId, String productId, String sellerId,
                               boolean active, boolean productPublished) {
        super(UUID.randomUUID().toString(), Instant.now(), skuId, "Variant");
        this.productId        = productId;
        this.sellerId         = sellerId;
        this.active           = active;
        this.productPublished = productPublished;
    }

    @Override
    public String getRoutingKey() { return "catalog.variant.created"; }

    @Override
    public Object getPayload() { return new Payload(getAggregateId(), productId, sellerId, active, productPublished); }

    public record Payload(String skuId, String productId, String sellerId,
                          boolean active, boolean productPublished) {}
}
