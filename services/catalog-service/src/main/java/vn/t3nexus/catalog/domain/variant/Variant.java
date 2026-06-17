package vn.t3nexus.catalog.domain.variant;

import vn.t3nexus.lib.common.domain.model.AbstractAggregateRoot;
import vn.t3nexus.lib.common.domain.model.AggregateRoot;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class Variant extends AbstractAggregateRoot<VariantId> implements AggregateRoot<VariantId> {

    private final String productId;
    private final VariantCombination combination;
    private String skuCode;
    private long price;
    private int stock;
    private VariantStatus status;
    private final List<SkuImage> images;
    private final Instant createdAt;
    private Instant updatedAt;

    private Variant(VariantId id,
                    String productId,
                    VariantCombination combination,
                    String skuCode,
                    long price,
                    int stock,
                    VariantStatus status,
                    List<SkuImage> images,
                    Instant createdAt,
                    Instant updatedAt) {
        setId(id);
        this.productId   = productId;
        this.combination = combination;
        this.skuCode     = skuCode;
        this.price       = price;
        this.stock       = stock;
        this.status      = status;
        this.images      = new ArrayList<>(images);
        this.createdAt   = createdAt;
        this.updatedAt   = updatedAt;
    }

    public static Variant create(VariantId id,
                                 String productId,
                                 VariantCombination combination,
                                 String skuCode,
                                 long price,
                                 int stock) {
        Instant now = Instant.now();
        return new Variant(id, productId, combination, skuCode, price, stock,
                VariantStatus.ACTIVE, List.of(), now, now);
    }

    public static Variant reconstitute(VariantId id,
                                       String productId,
                                       VariantCombination combination,
                                       String skuCode,
                                       long price,
                                       int stock,
                                       VariantStatus status,
                                       List<SkuImage> images,
                                       Instant createdAt,
                                       Instant updatedAt) {
        return new Variant(id, productId, combination, skuCode, price, stock,
                status, images, createdAt, updatedAt);
    }

    public void changePrice(long newPrice) {
        this.price     = newPrice;
        this.updatedAt = Instant.now();
        addDomainEvent(new VariantPriceChangedEvent(getId().getValue(), productId, newPrice));
    }

    public void deactivate() {
        if (this.status == VariantStatus.INACTIVE) return;
        this.status    = VariantStatus.INACTIVE;
        this.updatedAt = Instant.now();
        addDomainEvent(new VariantDeactivatedEvent(getId().getValue(), productId));
    }

    public void activate() {
        this.status    = VariantStatus.ACTIVE;
        this.updatedAt = Instant.now();
    }

    public void updateSkuCode(String skuCode) {
        this.skuCode   = skuCode;
        this.updatedAt = Instant.now();
    }

    public void addImage(SkuImage image) {
        images.add(image);
        this.updatedAt = Instant.now();
    }

    public void removeImage(SkuImageId imageId) {
        images.removeIf(img -> img.getId().equals(imageId));
        this.updatedAt = Instant.now();
    }

    public boolean isActive()                  { return status == VariantStatus.ACTIVE; }
    public String getProductId()               { return productId; }
    public VariantCombination getCombination() { return combination; }
    public String getSkuCode()                 { return skuCode; }
    public long getPrice()                     { return price; }
    public int getStock()                      { return stock; }
    public VariantStatus getStatus()           { return status; }
    public List<SkuImage> getImages()          { return List.copyOf(images); }
    public Instant getCreatedAt()              { return createdAt; }
    public Instant getUpdatedAt()              { return updatedAt; }
}
