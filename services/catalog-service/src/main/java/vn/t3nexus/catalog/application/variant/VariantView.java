package vn.t3nexus.catalog.application.variant;

import vn.t3nexus.catalog.domain.variant.Variant;
import vn.t3nexus.catalog.domain.variant.VariantStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Biến thể trả về sau lệnh ghi và nằm trong chi tiết sản phẩm. Biến thể không có phiên bản riêng (phiên bản ở sản phẩm). */
public record VariantView(UUID id, UUID productId, List<CombinationView> combination, long price, String sku,
                          VariantStatus status, Instant createdAt, Instant updatedAt) {

    public record CombinationView(UUID templateId, UUID optionId) {
    }

    public static VariantView of(Variant variant) {
        return new VariantView(variant.getId().getValue(), variant.getProductId().getValue(),
                variant.getCombination().stream()
                        .map(p -> new CombinationView(p.templateId().getValue(), p.optionId().getValue()))
                        .toList(),
                variant.getPrice(), variant.getSku(), variant.getStatus(), variant.getCreatedAt(),
                variant.getUpdatedAt());
    }
}
