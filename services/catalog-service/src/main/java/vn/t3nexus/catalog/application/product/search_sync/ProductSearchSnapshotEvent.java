package vn.t3nexus.catalog.application.product.search_sync;

import vn.t3nexus.lib.common.domain.model.AbstractDomainEvent;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * State-transfer event cho search-service — chở TOÀN BỘ state hiện tại của 1 sản phẩm (không phải "chuyện
 * gì vừa xảy ra" như domain event). Consumer upsert theo {@code searchVersion}: idempotent và chịu sai thứ
 * tự hoàn toàn. Xem {@code docs/service/search-service/analysis.md} Q2/Q3.
 * <br>Partition key = {@code productId} (aggregate_id của outbox).
 */
public class ProductSearchSnapshotEvent extends AbstractDomainEvent {

    private final Payload payload;

    public ProductSearchSnapshotEvent(Payload payload) {
        super(UUID.randomUUID().toString(), Instant.now(), payload.productId(), "Product");
        this.payload = payload;
    }

    @Override
    public String getRoutingKey() { return "catalog.product.search-snapshot"; }

    @Override
    public Object getPayload() { return payload; }

    public record Payload(
            String productId,
            long searchVersion,
            String sellerId,
            String name,
            String description,
            String brandId,
            String brandName,
            String categoryId,
            String status,
            boolean adminBlocked,
            Instant publishedAt,
            List<String> imageObjectKeys,
            List<Attribute> attributes,
            List<Variant> variants
    ) {}

    /**
     * Attribute cấp sản phẩm. {@code searchWeight}/{@code filterable}/{@code sortable} lấy từ {@code discovery}
     * của assignment ở category lá tại thời điểm phát — chỉ mang khả năng có bật hay không, KHÔNG mang cấu
     * hình hiển thị bộ lọc (presentation, buckets…), search-service đọc phần đó từ metadata category
     * (analysis.md AGG-CAT-03). Với attribute {@code variantDefining=true}, {@code values} là TẬP option sản phẩm
     * khai — search KHÔNG được chép xuống từng SKU (lấy từ {@link Variant#combination()} thay vào đó).
     */
    public record Attribute(
            String templateId,
            String inputType,
            boolean variantDefining,
            String searchWeight,
            boolean filterable,
            boolean sortable,
            List<Value> values
    ) {}

    /** {@code value}: optionId (SELECT) hoặc giá trị thô. {@code label}: displayValue (SELECT) hoặc = value. */
    public record Value(String value, String label) {}

    public record Variant(
            String skuId,
            long price,
            boolean active,
            List<String> imageObjectKeys,
            List<CombinationItem> combination
    ) {}

    public record CombinationItem(String templateId, String optionId, String label) {}
}
