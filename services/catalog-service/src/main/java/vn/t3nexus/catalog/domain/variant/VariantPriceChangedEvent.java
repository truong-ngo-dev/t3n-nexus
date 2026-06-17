package vn.t3nexus.catalog.domain.variant;

import vn.t3nexus.lib.common.domain.model.AbstractDomainEvent;

import java.time.Instant;
import java.util.UUID;

public class VariantPriceChangedEvent extends AbstractDomainEvent {

    private final String productId;
    private final long newPrice;

    public VariantPriceChangedEvent(String skuId, String productId, long newPrice) {
        super(UUID.randomUUID().toString(), Instant.now(), skuId, "Variant");
        this.productId = productId;
        this.newPrice  = newPrice;
    }

    @Override
    public String getRoutingKey() { return "catalog.variant.price-changed"; }

    @Override
    public Object getPayload() { return new Payload(getAggregateId(), productId, newPrice); }

    public String getProductId() { return productId; }
    public long getNewPrice()    { return newPrice; }

    public record Payload(String skuId, String productId, long newPrice) {}
}
