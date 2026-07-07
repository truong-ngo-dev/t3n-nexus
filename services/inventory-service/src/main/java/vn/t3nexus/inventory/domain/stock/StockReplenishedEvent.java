package vn.t3nexus.inventory.domain.stock;

import vn.t3nexus.lib.common.domain.model.AbstractDomainEvent;

import java.time.Instant;
import java.util.UUID;

public class StockReplenishedEvent extends AbstractDomainEvent {

    private final String skuId;
    private final String sellerId;
    private final int availableQuantity;

    public StockReplenishedEvent(String stockId, String skuId, String sellerId, int availableQuantity) {
        super(UUID.randomUUID().toString(), Instant.now(), stockId, "Stock");
        this.skuId             = skuId;
        this.sellerId          = sellerId;
        this.availableQuantity = availableQuantity;
    }

    @Override
    public String getRoutingKey() { return "inventory.stock.replenished"; }

    @Override
    public Object getPayload() {
        return new Payload(getAggregateId(), skuId, sellerId, availableQuantity);
    }

    public String getSkuId()          { return skuId; }
    public String getSellerId()       { return sellerId; }
    public int getAvailableQuantity() { return availableQuantity; }

    public record Payload(String stockId, String skuId, String sellerId, int availableQuantity) {}
}
