package vn.t3nexus.catalog.domain.product;

import vn.t3nexus.lib.common.domain.service.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductRepository extends Repository<Product, ProductId> {

    /** Sản phẩm theo mã và chủ; của người khác coi như không có. Đọc thường, không khóa dòng (khóa lạc quan). */
    Optional<Product> findOwned(ProductId id, UUID sellerId);

    /**
     * Như {@link #findOwned} nhưng khóa dòng sản phẩm tới hết giao dịch ({@code SELECT … FOR UPDATE}). Lệnh chạm biến
     * thể hoặc danh sách thuộc tính (có thể đổi trục) chỉ dùng đường này (ADR-0012).
     */
    Optional<Product> findOwnedForUpdate(ProductId id, UUID sellerId);

    /** Sản phẩm của một người bán, mới nhất trước; {@code status == null}: mọi trạng thái. {@code page} từ 0. */
    List<ProductSummary> findSummaries(UUID sellerId, ProductStatus status, int page, int size);

    long countBySeller(UUID sellerId, ProductStatus status);

    /** Có sản phẩm nào đang tham chiếu danh mục này không (mọi trạng thái); cho INV-CAT-98. */
    boolean existsByCategory(UUID categoryId);

    /**
     * Xóa nháp chưa từng đăng (feature 09). Biến thể phải xóa trước ({@code VariantRepository#deleteByProductId});
     * thuộc tính và ảnh xóa theo khóa ngoại CASCADE.
     */
    @Override
    void delete(ProductId id);
}
