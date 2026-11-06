package vn.t3nexus.catalog.domain.product;

import vn.t3nexus.catalog.domain.variant.VariantId;
import vn.t3nexus.lib.common.domain.model.AbstractDomainEvent;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Đơn vị bán được của sản phẩm đã thay đổi (EVT-CAT-02, ADR-0014).
 * Phát cho Tồn kho (C2a) khi trạng thái bán/hoạt động của biến thể thay đổi.
 * Không mang trường giá; khóa thông điệp (aggregateId) là productId.
 */
public final class SellableUnitChangedEvent extends AbstractDomainEvent {

    private final VariantId variantId;
    private final ProductId productId;
    private final UUID sellerId;
    private final boolean active;
    private final boolean visible;
    private final long version;

    public SellableUnitChangedEvent(UUID eventId, Instant occurredOn, VariantId variantId,
                                    ProductId productId, UUID sellerId, boolean active,
                                    boolean visible, long version) {
        super(Objects.requireNonNull(eventId, "eventId").toString(),
                Objects.requireNonNull(occurredOn, "occurredOn"),
                Objects.requireNonNull(productId, "productId").getValue().toString(),
                "Product");
        this.variantId = Objects.requireNonNull(variantId, "variantId");
        this.productId = productId;
        this.sellerId = Objects.requireNonNull(sellerId, "sellerId");
        this.active = active;
        this.visible = visible;
        this.version = version;
    }

    public VariantId variantId() { return variantId; }
    public ProductId productId() { return productId; }
    public UUID sellerId()       { return sellerId; }
    public boolean active()      { return active; }
    public boolean visible()     { return visible; }
    public long version()        { return version; }

    @Override
    public String getRoutingKey() {
        return "catalog.sellable-unit.changed";
    }

    @Override
    public Object getPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("variantId", variantId.getValue().toString());
        payload.put("productId", productId.getValue().toString());
        payload.put("sellerId", sellerId.toString());
        payload.put("active", Boolean.valueOf(active));
        payload.put("visible", Boolean.valueOf(visible));
        payload.put("version", Long.valueOf(version));
        payload.put("occurredAt", getOccurredOn().toString());
        return Collections.unmodifiableMap(payload);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[eventId=" + getEventId() + ", variantId=" + variantId.getValue()
                + ", productId=" + productId.getValue() + ", active=" + active + ", visible=" + visible
                + ", version=" + version + "]";
    }
}
