package vn.t3nexus.catalog.domain.variant;

import vn.t3nexus.catalog.domain.product.ProductId;
import vn.t3nexus.lib.common.domain.service.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VariantRepository extends Repository<Variant, VariantId> {

    /** Biến thể theo mã trong đúng sản phẩm đó; của sản phẩm khác coi như không có. */
    Optional<Variant> findByIdAndProduct(VariantId id, ProductId productId);

    /** Mọi biến thể của một sản phẩm, kể cả đã ngừng (tối đa số biến thể cho phép, nên nạp hết). */
    List<Variant> findByProductId(ProductId productId);

    /** Đếm cả biến thể đã ngừng (INV-CAT-93). */
    long countByProductId(ProductId productId);

    /** Kiểm sớm tổ hợp trùng; ràng buộc duy nhất {@code (sản phẩm, khóa tổ hợp)} là chốt cuối (ADR-0011). */
    boolean existsByProductAndCombinationKey(ProductId productId, String combinationKey);

    /** Kiểm sớm SKU đã dùng trong người bán (mọi sản phẩm); ràng buộc duy nhất {@code (người bán, SKU)} là chốt cuối. */
    boolean existsBySellerAndSku(UUID sellerId, String sku);

    /** Như {@link #existsBySellerAndSku} nhưng bỏ qua chính biến thể đang đổi SKU. */
    boolean existsBySellerAndSkuExcluding(UUID sellerId, String sku, VariantId excludingId);

    /** Xóa mọi biến thể của một sản phẩm (và tổ hợp của chúng): chỉ dùng khi xóa nháp chưa từng đăng (feature 09). */
    void deleteByProductId(ProductId productId);

    /** Biến thể không bao giờ bị xóa riêng lẻ, chỉ ngừng (INV-CAT-42); xóa nháp đi qua {@link #deleteByProductId}. */
    @Override
    default void delete(VariantId id) {
        throw new UnsupportedOperationException("Variant is never deleted individually: deactivate instead");
    }
}
