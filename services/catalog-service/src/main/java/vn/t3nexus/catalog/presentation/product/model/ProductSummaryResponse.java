package vn.t3nexus.catalog.presentation.product.model;

import java.time.Instant;
import java.util.List;

public record ProductSummaryResponse(
        String id,
        String name,
        String status,
        String categoryId,
        String brandId,
        String thumbnailObjectKey,
        Instant createdAt
) {
    public record PagedResponse(List<ProductSummaryResponse> items, long total, int page, int size) {}
}
