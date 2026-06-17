package vn.t3nexus.catalog.infrastructure.persistence.product;

import vn.t3nexus.catalog.domain.brand.BrandId;
import vn.t3nexus.catalog.domain.category.CategoryId;
import vn.t3nexus.catalog.domain.product.*;

import java.time.Instant;
import java.util.List;

public final class ProductMapper {

    private ProductMapper() {}

    public static Product toDomain(ProductJpaEntity entity,
                                   List<ProductAttributeValueJpaEntity> attrEntities,
                                   List<ProductImageJpaEntity> imageEntities) {
        List<ProductAttributeValue> attributeValues = attrEntities.stream()
                .map(a -> new ProductAttributeValue(
                        vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplateId.of(a.getTemplateId()),
                        a.getValue()))
                .toList();

        List<ProductImage> images = imageEntities.stream()
                .map(i -> ProductImage.reconstitute(
                        ProductImageId.of(i.getId()),
                        i.getObjectKey(),
                        i.getDisplayOrder()))
                .toList();

        WarrantyInfo warrantyInfo = entity.getWarrantyType() == null
                ? null
                : new WarrantyInfo(entity.getWarrantyMonths(), entity.getWarrantyType(), entity.getWarrantyCoverage());

        return Product.reconstitute(
                ProductId.of(entity.getId()),
                entity.getSellerId(),
                CategoryId.of(entity.getCategoryId()),
                BrandId.of(entity.getBrandId()),
                entity.getName(),
                entity.getDescription(),
                warrantyInfo,
                entity.getStatus(),
                attributeValues,
                images,
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    public static ProductJpaEntity toJpaEntity(Product product) {
        ProductJpaEntity entity = new ProductJpaEntity();
        entity.setId(product.getId().getValue());
        entity.setSellerId(product.getSellerId());
        entity.setCategoryId(product.getCategoryId().getValue());
        entity.setBrandId(product.getBrandId().getValue());
        entity.setName(product.getName());
        entity.setDescription(product.getDescription());
        entity.setStatus(product.getStatus());
        entity.setCreatedAt(product.getCreatedAt());
        entity.setUpdatedAt(product.getUpdatedAt());

        if (product.getWarrantyInfo() != null) {
            entity.setWarrantyMonths(product.getWarrantyInfo().months());
            entity.setWarrantyType(product.getWarrantyInfo().type());
            entity.setWarrantyCoverage(product.getWarrantyInfo().coverage());
        }

        return entity;
    }

    public static List<ProductAttributeValueJpaEntity> toAttributeValueEntities(Product product) {
        String productId = product.getId().getValue();
        return product.getAttributeValues().stream()
                .map(av -> {
                    ProductAttributeValueJpaEntity e = new ProductAttributeValueJpaEntity();
                    e.setProductId(productId);
                    e.setTemplateId(av.templateId().getValue());
                    e.setValue(av.value());
                    return e;
                })
                .toList();
    }

    public static List<ProductImageJpaEntity> toImageEntities(Product product) {
        String productId = product.getId().getValue();
        return product.getImages().stream()
                .map(img -> {
                    ProductImageJpaEntity e = new ProductImageJpaEntity();
                    e.setId(img.getId().getValue());
                    e.setProductId(productId);
                    e.setObjectKey(img.getObjectKey());
                    e.setDisplayOrder(img.getDisplayOrder());
                    e.setCreatedAt(Instant.now());
                    return e;
                })
                .toList();
    }
}
