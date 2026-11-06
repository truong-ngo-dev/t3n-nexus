package vn.t3nexus.catalog.domain.product;

import vn.t3nexus.catalog.domain.brand.BrandId;
import vn.t3nexus.catalog.domain.category.CategoryId;
import vn.t3nexus.lib.common.domain.model.AbstractDomainEvent;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Bản chụp trạng thái đầy đủ của sản phẩm (EVT-CAT-01, ADR-0014).
 * Phát cho Tìm kiếm (C1b) khi sản phẩm được đăng, gỡ đăng, đăng lại hoặc sửa khi đang PUBLISHED.
 */
public final class ProductSnapshotEvent extends AbstractDomainEvent {

    private final ProductId productId;
    private final long version;
    private final UUID sellerId;
    private final boolean visible;
    private final String title;
    private final String description;
    private final UUID brandId;
    private final UUID categoryId;
    private final List<Map<String, Object>> attributes;
    private final List<Map<String, Object>> images;
    private final List<Map<String, Object>> variants;

    public ProductSnapshotEvent(UUID eventId, Instant occurredOn, ProductId productId, long version,
                                UUID sellerId, boolean visible, String title, String description,
                                UUID brandId, UUID categoryId, List<Map<String, Object>> attributes,
                                List<Map<String, Object>> images, List<Map<String, Object>> variants) {
        super(Objects.requireNonNull(eventId, "eventId").toString(),
                Objects.requireNonNull(occurredOn, "occurredOn"),
                Objects.requireNonNull(productId, "productId").getValue().toString(),
                "Product");
        this.productId = productId;
        this.version = version;
        this.sellerId = Objects.requireNonNull(sellerId, "sellerId");
        this.visible = visible;
        this.title = Objects.requireNonNull(title, "title");
        this.description = description;
        this.brandId = brandId;
        this.categoryId = Objects.requireNonNull(categoryId, "categoryId");
        this.attributes = attributes == null ? List.of() : List.copyOf(attributes);
        this.images = images == null ? List.of() : List.copyOf(images);
        this.variants = variants == null ? List.of() : List.copyOf(variants);
    }

    public ProductId productId()                     { return productId; }
    public long version()                             { return version; }
    public UUID sellerId()                            { return sellerId; }
    public boolean visible()                          { return visible; }
    public String title()                             { return title; }
    public String description()                       { return description; }
    public UUID brandId()                             { return brandId; }
    public UUID categoryId()                          { return categoryId; }
    public List<Map<String, Object>> attributes()     { return attributes; }
    public List<Map<String, Object>> images()         { return images; }
    public List<Map<String, Object>> variants()       { return variants; }

    @Override
    public String getRoutingKey() {
        return "catalog.product.snapshot";
    }

    @Override
    public Object getPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("productId", productId.getValue().toString());
        payload.put("version", Long.valueOf(version));
        payload.put("sellerId", sellerId.toString());
        payload.put("visible", Boolean.valueOf(visible));
        payload.put("title", title);
        payload.put("description", description);
        payload.put("brandId", brandId == null ? null : brandId.toString());
        payload.put("categoryId", categoryId.toString());
        payload.put("attributes", attributes);
        payload.put("images", images);
        payload.put("variants", variants);
        payload.put("occurredAt", getOccurredOn().toString());
        return Collections.unmodifiableMap(payload);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[eventId=" + getEventId() + ", productId=" + productId.getValue()
                + ", version=" + version + ", visible=" + visible + "]";
    }
}
