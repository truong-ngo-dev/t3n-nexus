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
    private Instant publishedAt;
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
                    Instant publishedAt,
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
        this.publishedAt     = publishedAt;
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
                ProductStatus.DRAFT, false, attributeValues, List.of(), null, now, now);
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
                                       Instant publishedAt,
                                       Instant createdAt,
                                       Instant updatedAt) {
        return new Product(id, sellerId, categoryId, brandId, name, description, warrantyInfo,
                status, adminBlocked, attributeValues, images, publishedAt, createdAt, updatedAt);
    }

    // ───────────── Truy cập ─────────────

    /** Ranh giới sở hữu (analysis.md §4): mọi hành vi Seller chỉ hợp lệ trên sản phẩm do chính seller đó tạo. */
    public void assertOwnedBy(String callerSellerId) {
        if (!sellerId.equals(callerSellerId)) throw new DomainException(NOT_PRODUCT_OWNER);
    }

    /** INV-CAT-044 — định nghĩa DUY NHẤT của "được xem công khai"; mọi đường buyer đọc sản phẩm đều gọi hàm này. */
    public boolean isPubliclyVisible() {
        return status == ProductStatus.PUBLISHED && !adminBlocked;
    }

    // ───────────── Hành vi ─────────────
    // Sửa nội dung/ảnh, ngừng bán KHÔNG bị chi phối bởi trạng thái chặn — seller bị chặn vẫn phải tự khắc phục được;
    // chặn chỉ khoá đúng 1 thứ: đưa ra công khai (analysis.md AGG-CAT-04).

    public void update(String name, String description, WarrantyInfo warrantyInfo,
                       List<ProductAttributeValue> attributeValues) {
        this.name            = name;
        this.description     = description;
        this.warrantyInfo    = warrantyInfo;
        this.attributeValues.clear();
        this.attributeValues.addAll(attributeValues);
        this.updatedAt       = Instant.now();
        addDomainEvent(new ProductUpdatedEvent(getId().getValue()));
    }

    /** Chỉ từ "đang soạn"/"ngừng bán"; không khi đang bị chặn (INV-CAT-042). INV-CAT-96 kiểm ở use case. */
    public void publish(String brandName, List<String> activeSkuIds) {
        if (status == ProductStatus.PUBLISHED) throw new DomainException(INVALID_PRODUCT_TRANSITION);
        if (adminBlocked) throw new DomainException(PRODUCT_BLOCKED);
        Instant now = Instant.now();
        this.status    = ProductStatus.PUBLISHED;
        // Chỉ ghi lần publish ĐẦU TIÊN — unpublish rồi publish lại không làm sản phẩm "mới" trở lại.
        if (this.publishedAt == null) this.publishedAt = now;
        this.updatedAt = now;
        addDomainEvent(new ProductPublishedEvent(
                getId().getValue(), sellerId,
                categoryId.getValue(), brandId.getValue(), brandName,
                activeSkuIds, name));
    }

    /** Chỉ từ "đang bán" — bản nháp chưa từng bán thì không có gì để ngừng. */
    public void unpublish() {
        if (status != ProductStatus.PUBLISHED) throw new DomainException(INVALID_PRODUCT_TRANSITION);
        this.status    = ProductStatus.UNPUBLISHED;
        this.updatedAt = Instant.now();
        addDomainEvent(new ProductUnpublishedEvent(getId().getValue(), sellerId));
    }

    public void block(String reason) {
        if (adminBlocked) throw new DomainException(INVALID_PRODUCT_TRANSITION);
        this.adminBlocked = true;
        this.updatedAt    = Instant.now();
        addDomainEvent(new ProductBlockedEvent(getId().getValue(), sellerId, reason));
    }

    /** Không đụng trục ý muốn bán — nếu trước đó "đang bán" thì hiện lại ngay, không tự đưa về "đang bán". */
    public void unblock() {
        if (!adminBlocked) throw new DomainException(INVALID_PRODUCT_TRANSITION);
        this.adminBlocked = false;
        this.updatedAt    = Instant.now();
        addDomainEvent(new ProductUnblockedEvent(getId().getValue()));
    }

    public void addImage(ProductImage image) {
        images.add(image);
        this.updatedAt = Instant.now();
        addDomainEvent(new ProductUpdatedEvent(getId().getValue()));
    }

    public void removeImage(ProductImageId imageId) {
        boolean removed = images.removeIf(img -> img.getId().equals(imageId));
        if (!removed) throw new DomainException(IMAGE_NOT_FOUND);
        this.updatedAt = Instant.now();
        addDomainEvent(new ProductUpdatedEvent(getId().getValue()));
    }

    /** Xoá hẳn — chỉ khi "đang soạn" (INV-CAT-043); caller xoá khỏi repository sau khi gọi. */
    public void markDeleted() {
        if (status != ProductStatus.DRAFT) throw new DomainException(PRODUCT_NOT_DRAFT);
        addDomainEvent(new ProductDeletedEvent(getId().getValue()));
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
    public Instant getPublishedAt()                          { return publishedAt; }
    public Instant getCreatedAt()                            { return createdAt; }
    public Instant getUpdatedAt()                            { return updatedAt; }
}
