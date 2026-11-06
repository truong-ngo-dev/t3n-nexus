package vn.t3nexus.catalog.presentation.product.model;

import vn.t3nexus.catalog.application.product.AttributeView;
import vn.t3nexus.catalog.application.product.ImageView;
import vn.t3nexus.catalog.application.product.ProductView;
import vn.t3nexus.catalog.application.variant.VariantView;
import vn.t3nexus.catalog.domain.product.MissingPublishCondition;
import vn.t3nexus.catalog.domain.product.ProductStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Sản phẩm của người bán trả về (chi tiết và sau lệnh ghi). Không có mã người bán: chỉ chủ xem được. */
public record ProductResponse(UUID id, UUID categoryId, UUID brandId, String title, String description,
                              ProductStatus status, long version, List<AttributeView> attributes,
                              List<ImageView> images, List<VariantView> variants,
                              List<MissingCondition> missingPublishConditions, Instant publishedAt,
                              Instant createdAt, Instant updatedAt) {

    public record MissingCondition(MissingPublishCondition.Code code, UUID templateId) {
    }

    public static ProductResponse from(ProductView view) {
        List<MissingCondition> missing = view.missingPublishConditions().stream()
                .map(c -> new MissingCondition(c.code(), c.templateId() == null ? null : c.templateId().getValue()))
                .toList();
        return new ProductResponse(view.id(), view.categoryId(), view.brandId(), view.title(), view.description(),
                view.status(), view.version(), view.attributes(), view.images(), view.variants(), missing,
                view.publishedAt(), view.createdAt(), view.updatedAt());
    }
}
