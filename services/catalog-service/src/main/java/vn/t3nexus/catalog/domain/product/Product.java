package vn.t3nexus.catalog.domain.product;

import vn.t3nexus.lib.common.domain.exception.DomainException;
import vn.t3nexus.lib.common.domain.model.AbstractAggregateRoot;
import vn.t3nexus.lib.common.domain.model.AggregateRoot;
import vn.t3nexus.catalog.domain.brand.BrandId;
import vn.t3nexus.catalog.domain.category.CategoryId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static vn.t3nexus.catalog.domain.product.ProductErrorCode.*;

public class Product extends AbstractAggregateRoot<ProductId> implements AggregateRoot<ProductId> {

    private String sellerId;
    private CategoryId categoryId;
    private BrandId brandId;
    private String name;
    private String description;
    private WarrantyInfo warrantyInfo;
    private ProductStatus status;
    private boolean adminBlocked;
    private final List<ProductAttributeValue> attributeValues;
    private final List<ProductImage> images;
    private final Instant createdAt;
    private Instant updatedAt;

    private Product(ProductId id,
                    String sellerId,
                    CategoryId categoryId,
                    BrandId brandId,
                    String name,
                    String description,
                    WarrantyInfo warrantyInfo,
                    ProductStatus status,
                    boolean adminBlocked,
                    List<ProductAttributeValue> attributeValues,
                    List<ProductImage> images,
                    Instant createdAt,
                    Instant updatedAt) {
        setId(id);
        this.sellerId        = sellerId;
        this.categoryId      = categoryId;
        this.brandId         = brandId;
        this.name            = name;
        this.description     = description;
        this.warrantyInfo    = warrantyInfo;
        this.status          = status;
        this.adminBlocked    = adminBlocked;
        this.attributeValues = new ArrayList<>(attributeValues);
        this.images          = new ArrayList<>(images);
        this.createdAt       = createdAt;
        this.updatedAt       = updatedAt;
    }

    public static Product create(ProductId id,
                                 String sellerId,
                                 CategoryId categoryId,
                                 BrandId brandId,
                                 String name,
                                 String description,
                                 WarrantyInfo warrantyInfo,
                                 List<ProductAttributeValue> attributeValues) {
        Instant now = Instant.now();
        return new Product(id, sellerId, categoryId, brandId, name, description, warrantyInfo,
                ProductStatus.DRAFT, false, attributeValues, List.of(), now, now);
    }

    public static Product reconstitute(ProductId id,
                                       String sellerId,
                                       CategoryId categoryId,
                                       BrandId brandId,
                                       String name,
                                       String description,
                                       WarrantyInfo warrantyInfo,
                                       ProductStatus status,
                                       boolean adminBlocked,
                                       List<ProductAttributeValue> attributeValues,
                                       List<ProductImage> images,
                                       Instant createdAt,
                                       Instant updatedAt) {
        return new Product(id, sellerId, categoryId, brandId, name, description, warrantyInfo,
                status, adminBlocked, attributeValues, images, createdAt, updatedAt);
    }

    public void update(String name, String description, WarrantyInfo warrantyInfo,
                       List<ProductAttributeValue> attributeValues) {
        guardNotBlocked();
        this.name            = name;
        this.description     = description;
        this.warrantyInfo    = warrantyInfo;
        this.attributeValues.clear();
        this.attributeValues.addAll(attributeValues);
        this.updatedAt       = Instant.now();
        addDomainEvent(new ProductUpdatedEvent(getId().getValue()));
    }

    public void updateCategory(CategoryId newCategoryId) {
        guardNotBlocked();
        this.categoryId = newCategoryId;
        this.updatedAt  = Instant.now();
        addDomainEvent(new ProductUpdatedEvent(getId().getValue()));
    }

    public void publish(String brandName, List<String> activeSkuIds) {
        guardNotBlocked();
        this.status    = ProductStatus.PUBLISHED;
        this.updatedAt = Instant.now();
        addDomainEvent(new ProductPublishedEvent(
                getId().getValue(), sellerId,
                categoryId.getValue(), brandId.getValue(), brandName,
                activeSkuIds, name));
    }

    public void unpublish() {
        guardNotBlocked();
        this.status    = ProductStatus.UNPUBLISHED;
        this.updatedAt = Instant.now();
        addDomainEvent(new ProductUnpublishedEvent(getId().getValue(), sellerId));
    }

    public void block(String reason) {
        this.adminBlocked = true;
        this.updatedAt    = Instant.now();
        addDomainEvent(new ProductBlockedEvent(getId().getValue(), sellerId, reason));
    }

    public void unblock() {
        this.adminBlocked = false;
        this.updatedAt    = Instant.now();
        addDomainEvent(new ProductUnblockedEvent(getId().getValue()));
    }

    public void addImage(ProductImage image) {
        guardNotBlocked();
        images.add(image);
        this.updatedAt = Instant.now();
    }

    public void removeImage(ProductImageId imageId) {
        guardNotBlocked();
        boolean removed = images.removeIf(img -> img.getId().equals(imageId));
        if (!removed) throw new DomainException(IMAGE_NOT_FOUND);
        this.updatedAt = Instant.now();
    }

    private void guardNotBlocked() {
        if (adminBlocked) throw new DomainException(PRODUCT_BLOCKED);
    }

    public String getSellerId()                              { return sellerId; }
    public CategoryId getCategoryId()                        { return categoryId; }
    public BrandId getBrandId()                              { return brandId; }
    public String getName()                                  { return name; }
    public String getDescription()                           { return description; }
    public WarrantyInfo getWarrantyInfo()                    { return warrantyInfo; }
    public ProductStatus getStatus()                         { return status; }
    public boolean isAdminBlocked()                          { return adminBlocked; }
    public List<ProductAttributeValue> getAttributeValues()  { return List.copyOf(attributeValues); }
    public List<ProductImage> getImages()                    { return List.copyOf(images); }
    public Instant getCreatedAt()                            { return createdAt; }
    public Instant getUpdatedAt()                            { return updatedAt; }
}
