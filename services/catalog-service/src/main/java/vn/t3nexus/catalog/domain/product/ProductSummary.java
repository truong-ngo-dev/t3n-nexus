package vn.t3nexus.catalog.domain.product;

import java.time.Instant;
import java.util.UUID;

/** Một dòng của danh sách "sản phẩm của tôi": đọc thẳng từ bảng, kèm số biến thể và khoảng giá. */
public record ProductSummary(UUID id, String title, ProductStatus status, long version, long variantCount,
                             Long minPrice, Long maxPrice, Instant updatedAt) {
}
