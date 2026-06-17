package vn.t3nexus.catalog.presentation.variant.model;

import java.util.List;

public record VariantResponse(
        String skuId,
        String productId,
        List<CombinationItemResponse> combination,
        String skuCode,
        long price,
        int stock,
        String status,
        List<SkuImageResponse> images
) {
    public record CombinationItemResponse(String templateId, String optionId) {}
    public record SkuImageResponse(String imageId, String objectKey, int displayOrder) {}
}
