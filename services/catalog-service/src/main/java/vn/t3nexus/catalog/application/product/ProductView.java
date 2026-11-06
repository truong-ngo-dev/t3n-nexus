package vn.t3nexus.catalog.application.product;

import vn.t3nexus.catalog.application.variant.VariantView;
import vn.t3nexus.catalog.domain.product.MissingPublishCondition;
import vn.t3nexus.catalog.domain.product.ProductStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Sản phẩm trả về sau lệnh ghi (kèm phiên bản sau thay đổi) và cho đọc chi tiết. Kèm biến thể (kể cả đã ngừng) và điều kiện còn thiếu để đăng. */
public record ProductView(UUID id, UUID categoryId, UUID brandId, String title, String description,
                          ProductStatus status, long version, List<AttributeView> attributes,
                          List<ImageView> images, List<VariantView> variants,
                          List<MissingPublishCondition> missingPublishConditions, Instant publishedAt,
                          Instant createdAt, Instant updatedAt) {
}
