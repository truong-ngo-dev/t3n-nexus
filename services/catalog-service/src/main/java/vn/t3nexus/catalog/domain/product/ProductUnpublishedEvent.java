package vn.t3nexus.catalog.domain.product;

import vn.t3nexus.lib.common.domain.model.AbstractDomainEvent;

import java.time.Instant;
import java.util.UUID;

public class ProductUnpublishedEvent extends AbstractDomainEvent {

    private final String sellerId;

    public ProductUnpublishedEvent(String productId, String sellerId) {
        super(UUID.randomUUID().toString(), Instant.now(), productId, "Product");
        this.sellerId = sellerId;
    }

    @Override
    public String getRoutingKey() { return "catalog.product.unpublished"; }

    @Override
    public Object getPayload() { return new Payload(getAggregateId(), sellerId); }

    public String getSellerId() { return sellerId; }

    public record Payload(String productId, String sellerId) {}
}
