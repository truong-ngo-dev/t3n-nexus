package vn.t3nexus.inventory.domain.stock;

import vn.t3nexus.lib.common.domain.model.AbstractDomainEvent;

import java.time.Instant;
import java.util.UUID;

public class StockDepletedEvent extends AbstractDomainEvent {

    private final String skuId;
    private final String sellerId;

    public StockDepletedEvent(String stockId, String skuId, String sellerId) {
        super(UUID.randomUUID().toString(), Instant.now(), stockId, "Stock");
        this.skuId    = skuId;
        this.sellerId = sellerId;
    }

    @Override
    public String getRoutingKey() { return "inventory.stock.depleted"; }

    @Override
    public Object getPayload() {
        return new Payload(getAggregateId(), skuId, sellerId);
    }

    public String getSkuId()    { return skuId; }
    public String getSellerId() { return sellerId; }

    public record Payload(String stockId, String skuId, String sellerId) {}
}
