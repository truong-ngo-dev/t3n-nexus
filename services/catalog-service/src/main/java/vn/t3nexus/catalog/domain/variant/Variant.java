package vn.t3nexus.catalog.domain.variant;

import vn.t3nexus.catalog.domain.CatalogErrorCode;
import vn.t3nexus.catalog.domain.CatalogFieldException;
import vn.t3nexus.catalog.domain.product.ProductId;
import vn.t3nexus.catalog.domain.product.ProductLimits;
import vn.t3nexus.lib.common.domain.exception.DomainException;
import vn.t3nexus.lib.common.domain.model.AbstractAggregateRoot;
import vn.t3nexus.lib.common.domain.model.AggregateRoot;

import java.time.Instant;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Một đơn vị bán được của sản phẩm (AGG-CAT-05; INV-CAT-40 đến 44, 92): tổ hợp giá trị trục, giá niêm yết, SKU, trạng thái.
 * Tổ hợp bất biến sau khi tạo. Không có phiên bản riêng: mọi lệnh biến thể tuần tự qua dòng sản phẩm và tăng phiên bản
 * sản phẩm (ADR-0012). {@code sellerId} sao từ sản phẩm để ràng buộc SKU duy nhất theo người bán. Không phát event ở 07.
 */
public class Variant extends AbstractAggregateRoot<VariantId> implements AggregateRoot<VariantId> {

    private final ProductId productId;
    private final UUID sellerId;
    private final List<VariantAttributePair> combination;
    private final String combinationKey;
    private long price;
    private String sku;
    private VariantStatus status;
    private final Instant createdAt;
    private Instant updatedAt;

    private Variant(VariantId id, ProductId productId, UUID sellerId, List<VariantAttributePair> combination,
                    long price, String sku, VariantStatus status, Instant createdAt, Instant updatedAt) {
        setId(id);
        this.productId      = productId;
        this.sellerId       = sellerId;
        this.combination    = List.copyOf(combination);
        this.combinationKey = keyOf(combination);
        this.price          = price;
        this.sku            = sku;
        this.status         = status;
        this.createdAt      = createdAt;
        this.updatedAt      = updatedAt;
    }

    /** Tổ hợp đã được {@link VariantCombinationPolicy} kiểm với sản phẩm. Biến thể mới luôn {@code ACTIVE}. */
    public static Variant create(VariantId id, ProductId productId, UUID sellerId, List<VariantAttributePair> combination,
                                 long price, String sku, ProductLimits limits, Instant now) {
        return new Variant(id, productId, sellerId, combination, validPrice(price, limits), validSku(sku, limits),
                VariantStatus.ACTIVE, now, now);
    }

    public static Variant reconstitute(VariantId id, ProductId productId, UUID sellerId,
                                       List<VariantAttributePair> combination, long price, String sku,
                                       VariantStatus status, Instant createdAt, Instant updatedAt) {
        return new Variant(id, productId, sellerId, combination, price, sku, status, createdAt, updatedAt);
    }

    /**
     * Khóa tổ hợp (INV-CAT-92): các mã tùy chọn sắp theo mã mẫu tăng dần, nối bằng dấu phẩy. Không phụ thuộc thứ tự cặp;
     * tổ hợp rỗng (biến thể mặc định, INV-CAT-44) cho khóa rỗng.
     */
    public static String keyOf(List<VariantAttributePair> pairs) {
        return pairs.stream()
                .sorted(Comparator.comparing(p -> p.templateId().getValue().toString()))
                .map(p -> p.optionId().getValue().toString())
                .collect(Collectors.joining(","));
    }

    // ───────────── Hành vi ─────────────

    public void changePrice(long newPrice, ProductLimits limits, Instant now) {
        this.price     = validPrice(newPrice, limits);
        this.updatedAt = now;
    }

    /** {@code null}: bỏ SKU. */
    public void changeSku(String newSku, ProductLimits limits, Instant now) {
        this.sku       = validSku(newSku, limits);
        this.updatedAt = now;
    }

    public void deactivate(Instant now) {
        if (status == VariantStatus.INACTIVE) {
            throw new DomainException(CatalogErrorCode.CATALOG_INVALID_STATE);
        }
        this.status    = VariantStatus.INACTIVE;
        this.updatedAt = now;
    }

    public void activate(Instant now) {
        if (status == VariantStatus.ACTIVE) {
            throw new DomainException(CatalogErrorCode.CATALOG_INVALID_STATE);
        }
        this.status    = VariantStatus.ACTIVE;
        this.updatedAt = now;
    }

    public boolean isActive() {
        return status == VariantStatus.ACTIVE;
    }

    // ───────────── Nội bộ ─────────────

    private static long validPrice(long price, ProductLimits limits) {
        if (price < ProductLimits.PRICE_MIN || price > limits.priceMax()) {
            throw new CatalogFieldException("price");
        }
        return price;
    }

    private static String validSku(String raw, ProductLimits limits) {
        if (raw == null) {
            return null;
        }
        String sku = raw.strip();
        int length = sku.codePointCount(0, sku.length());
        if (length < ProductLimits.SKU_MIN || length > limits.skuMax()) {
            throw new CatalogFieldException("sku");
        }
        return sku;
    }

    // ───────────── Getters ─────────────

    public ProductId getProductId()                    { return productId; }
    public UUID getSellerId()                          { return sellerId; }
    public List<VariantAttributePair> getCombination() { return Collections.unmodifiableList(combination); }
    public String getCombinationKey()                  { return combinationKey; }
    public long getPrice()                             { return price; }
    public String getSku()                             { return sku; }
    public VariantStatus getStatus()                   { return status; }
    public Instant getCreatedAt()                      { return createdAt; }
    public Instant getUpdatedAt()                      { return updatedAt; }
}
