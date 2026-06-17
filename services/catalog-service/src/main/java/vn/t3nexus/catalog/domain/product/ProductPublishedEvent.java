package vn.t3nexus.catalog.domain.product;

import vn.t3nexus.lib.common.domain.model.AbstractDomainEvent;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class ProductPublishedEvent extends AbstractDomainEvent {

    private final String sellerId;
    private final String categoryId;
    private final String brandId;
    private final String brandName;
    private final List<String> activeSkuIds;
    private final String productName;

    public ProductPublishedEvent(String productId, String sellerId, String categoryId,
                                 String brandId, String brandName,
                                 List<String> activeSkuIds, String productName) {
        super(UUID.randomUUID().toString(), Instant.now(), productId, "Product");
        this.sellerId     = sellerId;
        this.categoryId   = categoryId;
        this.brandId      = brandId;
        this.brandName    = brandName;
        this.activeSkuIds = List.copyOf(activeSkuIds);
        this.productName  = productName;
    }

    @Override
    public String getRoutingKey() { return "catalog.product.published"; }

    @Override
    public Object getPayload() {
        return new Payload(getAggregateId(), sellerId, categoryId, brandName, activeSkuIds, productName);
    }

    public String getSellerId()           { return sellerId; }
    public String getCategoryId()         { return categoryId; }
    public String getBrandId()            { return brandId; }
    public String getBrandName()          { return brandName; }
    public List<String> getActiveSkuIds() { return activeSkuIds; }
    public String getProductName()        { return productName; }

    public record Payload(
            String productId,
            String sellerId,
            String categoryId,
            String brandName,
            List<String> skuIds,
            String name
    ) {}
}
