package vn.t3nexus.catalog.domain.product;

import vn.t3nexus.catalog.domain.CatalogErrorCode;
import vn.t3nexus.catalog.domain.CatalogFieldException;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeOptionId;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplateId;
import vn.t3nexus.catalog.domain.brand.BrandId;
import vn.t3nexus.catalog.domain.category.CategoryId;
import vn.t3nexus.lib.common.domain.exception.DomainException;
import vn.t3nexus.lib.common.domain.model.AbstractAggregateRoot;
import vn.t3nexus.lib.common.domain.model.AggregateRoot;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Sản phẩm của một người bán (AGG-CAT-04; INV-CAT-30, 31, 33, 36). Feature 07 chỉ có bản nháp: tiêu đề, mô tả, thương hiệu
 * tùy chọn, giá trị thuộc tính (trong đó có thể có tối đa 2 trục biến thể). Biến thể là aggregate riêng; mọi thay đổi
 * biến thể ghi nhận qua {@link #recordChange} để phiên bản sản phẩm là một số chung (ADR-0012).
 * <br>Không phát event ở feature 07. Phiên bản: {@code null} khi mới, {@link #nextVersion()} là phiên bản sau khi lưu.
 */
public class Product extends AbstractAggregateRoot<ProductId> implements AggregateRoot<ProductId> {

    private final UUID sellerId;
    private final CategoryId categoryId;
    private BrandId brandId;
    private String title;
    private String description;
    private ProductStatus status;
    private final List<ProductAttribute> attributes;
    private final List<ProductImage> images;
    private Instant publishedAt;
    private final Long version;
    private final Instant createdAt;
    private Instant updatedAt;

    private Product(ProductId id, UUID sellerId, CategoryId categoryId, BrandId brandId, String title,
                    String description, ProductStatus status, List<ProductAttribute> attributes,
                    List<ProductImage> images, Instant publishedAt, Long version, Instant createdAt, Instant updatedAt) {
        setId(id);
        this.sellerId    = sellerId;
        this.categoryId  = categoryId;
        this.brandId     = brandId;
        this.title       = title;
        this.description = description;
        this.status      = status;
        this.attributes  = new ArrayList<>(attributes);
        this.images      = new ArrayList<>(images);
        this.publishedAt = publishedAt;
        this.version     = version;
        this.createdAt   = createdAt;
        this.updatedAt   = updatedAt;
    }

    // ───────────── Factory ─────────────

    /** Sản phẩm nháp mới. Danh mục, thương hiệu và thuộc tính đã được use case kiểm với dữ liệu của chúng. */
    public static Product create(ProductId id, UUID sellerId, CategoryId categoryId, BrandId brandId, String title,
                                 String description, List<ProductAttribute> attributes, ProductLimits limits,
                                 Instant now) {
        return new Product(id, sellerId, categoryId, brandId, validTitle(title, limits),
                validDescription(description, limits), ProductStatus.DRAFT, attributes, List.of(), null, null, now, now);
    }

    public static Product reconstitute(ProductId id, UUID sellerId, CategoryId categoryId, BrandId brandId, String title,
                                       String description, ProductStatus status, List<ProductAttribute> attributes,
                                       Long version, Instant createdAt, Instant updatedAt) {
        return reconstitute(id, sellerId, categoryId, brandId, title, description, status, attributes,
                List.of(), version, createdAt, updatedAt);
    }

    public static Product reconstitute(ProductId id, UUID sellerId, CategoryId categoryId, BrandId brandId, String title,
                                       String description, ProductStatus status, List<ProductAttribute> attributes,
                                       List<ProductImage> images, Long version, Instant createdAt, Instant updatedAt) {
        return reconstitute(id, sellerId, categoryId, brandId, title, description, status, attributes, images,
                null, version, createdAt, updatedAt);
    }

    public static Product reconstitute(ProductId id, UUID sellerId, CategoryId categoryId, BrandId brandId, String title,
                                       String description, ProductStatus status, List<ProductAttribute> attributes,
                                       List<ProductImage> images, Instant publishedAt, Long version, Instant createdAt, Instant updatedAt) {
        return new Product(id, sellerId, categoryId, brandId, title, description, status, attributes, images, publishedAt,
                version, createdAt, updatedAt);
    }

    // ───────────── Hành vi ─────────────

    public boolean ownedBy(UUID accountId) {
        return sellerId.equals(accountId);
    }

    public void changeTitle(String newTitle, ProductLimits limits, Instant now) {
        this.title     = validTitle(newTitle, limits);
        this.updatedAt = now;
    }

    /** Mô tả rỗng hoặc chỉ khoảng trắng thành không có; ký tự đặc biệt lưu nguyên như chữ. */
    public void changeDescription(String newDescription, ProductLimits limits, Instant now) {
        this.description = validDescription(newDescription, limits);
        this.updatedAt   = now;
    }

    /** {@code null}: bỏ thương hiệu. Thương hiệu tồn tại và đang dùng do use case kiểm. */
    public void changeBrand(BrandId newBrandId, Instant now) {
        this.brandId   = newBrandId;
        this.updatedAt = now;
    }

    /**
     * Thay toàn bộ danh sách thuộc tính (đã qua {@link AttributeValueChecker}). Khi sản phẩm đã có biến thể, tập thuộc tính
     * làm trục phải giữ nguyên và không bỏ giá trị trục đang được biến thể dùng, kể cả biến thể đã ngừng (INV-CAT-33).
     */
    public void replaceAttributes(List<ProductAttribute> newAttributes, VariantUsage usage, Instant now) {
        if (usage.hasVariants()) {
            checkAxesAgainstVariants(newAttributes, usage);
        }
        this.attributes.clear();
        this.attributes.addAll(newAttributes);
        this.updatedAt = now;
    }

    /**
     * Đăng sản phẩm: trạng thái phải là DRAFT hoặc UNPUBLISHED.
     */
    public void publish(Instant now) {
        if (status != ProductStatus.DRAFT && status != ProductStatus.UNPUBLISHED) {
            throw new DomainException(CatalogErrorCode.CATALOG_INVALID_STATE);
        }
        this.status = ProductStatus.PUBLISHED;
        if (this.publishedAt == null) {
            this.publishedAt = now;
        }
        recordChange(now);
    }

    /**
     * Gỡ đăng sản phẩm: trạng thái phải là PUBLISHED.
     */
    public void unpublish(Instant now) {
        if (status != ProductStatus.PUBLISHED) {
            throw new DomainException(CatalogErrorCode.CATALOG_INVALID_STATE);
        }
        this.status = ProductStatus.UNPUBLISHED;
        recordChange(now);
    }

    /**
     * Kiểm tra điều kiện xóa: chỉ xóa được sản phẩm nháp chưa từng đăng.
     */
    public void assertDeletable() {
        if (status != ProductStatus.DRAFT || publishedAt != null) {
            throw new DomainException(ProductErrorCode.PRODUCT_NOT_DELETABLE);
        }
    }

    /**
     * Kiểm tra xem sản phẩm đang ở trạng thái đã đăng hay không.
     */
    public boolean isPublished() {
        return status == ProductStatus.PUBLISHED;
    }

    /**
     * Thêm ảnh vào cuối danh sách. Bỏ qua position của tham số image, gán position = images.size().
     */
    public void addImage(ProductImage image, int maxImages, Instant now) {
        if (this.images.size() >= maxImages) {
            throw new DomainException(ProductErrorCode.IMAGE_LIMIT_REACHED);
        }
        ProductImage positioned = image.withPosition(this.images.size());
        this.images.add(positioned);
        recordChange(now);
    }

    /**
     * Xóa ảnh theo mã ảnh, dồn lại position liền nhau từ 0 theo thứ tự cũ.
     */
    public ProductImage removeImage(ProductImageId imageId, Instant now) {
        int index = -1;
        for (int i = 0; i < this.images.size(); i++) {
            if (this.images.get(i).getId().equals(imageId)) {
                index = i;
                break;
            }
        }
        if (index == -1) {
            throw new DomainException(ProductErrorCode.IMAGE_NOT_FOUND);
        }
        if (status == ProductStatus.PUBLISHED && this.images.size() == 1) {
            throw new DomainException(ProductErrorCode.PRODUCT_LAST_IMAGE);
        }
        ProductImage removed = this.images.remove(index);
        for (int i = 0; i < this.images.size(); i++) {
            this.images.set(i, this.images.get(i).withPosition(i));
        }
        recordChange(now);
        return removed;
    }

    /**
     * Sắp xếp lại thứ tự ảnh. Danh sách orderedIds phải có đúng mọi mã ảnh hiện có, mỗi mã một lần.
     */
    public boolean reorderImages(List<ProductImageId> orderedIds, Instant now) {
        if (orderedIds == null || orderedIds.size() != this.images.size()) {
            throw new DomainException(ProductErrorCode.IMAGE_ORDER_INVALID);
        }
        Set<ProductImageId> set = new java.util.HashSet<>();
        Map<ProductImageId, ProductImage> byId = new LinkedHashMap<>();
        for (ProductImage img : this.images) {
            byId.put(img.getId(), img);
        }
        for (ProductImageId id : orderedIds) {
            if (id == null || !byId.containsKey(id) || !set.add(id)) {
                throw new DomainException(ProductErrorCode.IMAGE_ORDER_INVALID);
            }
        }
        boolean sameOrder = true;
        for (int i = 0; i < this.images.size(); i++) {
            if (!this.images.get(i).getId().equals(orderedIds.get(i))) {
                sameOrder = false;
                break;
            }
        }
        if (sameOrder) {
            return false;
        }
        List<ProductImage> reordered = new ArrayList<>(this.images.size());
        for (int i = 0; i < orderedIds.size(); i++) {
            ProductImage img = byId.get(orderedIds.get(i));
            reordered.add(img.withPosition(i));
        }
        this.images.clear();
        this.images.addAll(reordered);
        recordChange(now);
        return true;
    }

    /**
     * Biến thể của sản phẩm vừa thay đổi: làm dòng sản phẩm đổi (thời điểm sửa) để phiên bản chung tăng đúng một lần khi
     * lưu (INV-CAT-36, ADR-0012).
     */
    public void recordChange(Instant now) {
        // Tăng nghiêm ngặt: hai lệnh liên tiếp trong cùng một vi giây (hoặc đồng hồ cố định) vẫn làm dòng đổi thật
        this.updatedAt = now.isAfter(updatedAt) ? now : updatedAt.plus(1, ChronoUnit.MICROS);
    }

    /** Phiên bản SAU thay đổi sắp lưu: {@code 0} khi chưa lưu, ngược lại {@code version() + 1}. */
    public long nextVersion() {
        return version == null ? 0L : version + 1;
    }

    // ───────────── Nội bộ ─────────────

    private void checkAxesAgainstVariants(List<ProductAttribute> newAttributes, VariantUsage usage) {
        Map<AttributeTemplateId, Set<AttributeOptionId>> oldAxes = axesOf(attributes);
        Map<AttributeTemplateId, Set<AttributeOptionId>> newAxes = axesOf(newAttributes);
        if (!oldAxes.keySet().equals(newAxes.keySet())) {
            throw new DomainException(ProductErrorCode.PRODUCT_AXES_LOCKED);
        }
        for (AttributeTemplateId axis : oldAxes.keySet()) {
            Set<AttributeOptionId> used = usage.usedOptions().getOrDefault(axis, Set.of());
            if (!newAxes.get(axis).containsAll(used)) {
                throw new DomainException(ProductErrorCode.PRODUCT_AXIS_VALUE_IN_USE);
            }
        }
    }

    private static Map<AttributeTemplateId, Set<AttributeOptionId>> axesOf(List<ProductAttribute> list) {
        Map<AttributeTemplateId, Set<AttributeOptionId>> axes = new LinkedHashMap<>();
        list.stream().filter(ProductAttribute::axis).forEach(a -> axes.put(a.templateId(), a.optionIds()));
        return axes;
    }

    private static String validTitle(String raw, ProductLimits limits) {
        if (raw == null) {
            throw new CatalogFieldException("title");
        }
        String title = raw.strip();
        int length = title.codePointCount(0, title.length());
        if (length < limits.titleMin() || length > limits.titleMax()) {
            throw new CatalogFieldException("title");
        }
        return title;
    }

    private static String validDescription(String raw, ProductLimits limits) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        if (raw.codePointCount(0, raw.length()) > limits.descriptionMax()) {
            throw new CatalogFieldException("description");
        }
        return raw;
    }

    // ───────────── Getters ─────────────

    public UUID getSellerId()                    { return sellerId; }
    public CategoryId getCategoryId()            { return categoryId; }
    public BrandId getBrandId()                  { return brandId; }
    public String getTitle()                     { return title; }
    public String getDescription()               { return description; }
    public ProductStatus getStatus()             { return status; }
    public List<ProductAttribute> getAttributes() { return Collections.unmodifiableList(attributes); }
    public List<ProductImage> getImages()        { return Collections.unmodifiableList(images); }
    public Instant getPublishedAt()              { return publishedAt; }
    public Long version()                        { return version; }
    public Instant getCreatedAt()                { return createdAt; }
    public Instant getUpdatedAt()                { return updatedAt; }
}
