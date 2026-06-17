package vn.t3nexus.catalog.presentation.product.model;

import java.util.List;

public record ProductResponse(
        String id,
        String sellerId,
        String categoryId,
        String brandId,
        String name,
        String description,
        String status,
        WarrantyInfoResponse warrantyInfo,
        List<AttributeValueResponse> attributeValues,
        List<ProductImageResponse> images
) {
    public record WarrantyInfoResponse(int months, String type, String coverage) {}
    public record AttributeValueResponse(String templateId, String value) {}
    public record ProductImageResponse(String imageId, String objectKey, int displayOrder) {}
}
