package vn.t3nexus.catalog.presentation.product.model;

import java.util.List;

public record ProductResponse(
        String id,
        String sellerId,
        String categoryId,
        String categoryName,
        String brandId,
        String brandName,
        String name,
        String description,
        String status,
        WarrantyInfoResponse warrantyInfo,
        List<AttributeValueResponse> attributeValues,
        List<ProductImageResponse> images
) {
    public record WarrantyInfoResponse(int months, String type, String coverage) {}

    public record AttributeValueResponse(
            String templateId,
            String templateName,
            String templateDisplayName,
            List<AttributeValueItemResponse> values,
            boolean isVariantDefining) {}

    public record AttributeValueItemResponse(String value, String displayValue) {}

    public record ProductImageResponse(String imageId, String objectKey, int displayOrder) {}
}
