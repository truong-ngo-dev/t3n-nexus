package vn.t3nexus.catalog.infrastructure.persistence.product;

import vn.t3nexus.catalog.domain.attributetemplate.AttributeOptionId;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplateId;
import vn.t3nexus.catalog.domain.brand.BrandId;
import vn.t3nexus.catalog.domain.category.CategoryId;
import vn.t3nexus.catalog.domain.product.Product;
import vn.t3nexus.catalog.domain.product.ProductAttribute;
import vn.t3nexus.catalog.domain.product.ProductImage;
import vn.t3nexus.catalog.domain.product.ProductImageId;
import vn.t3nexus.catalog.domain.product.ProductId;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class ProductMapper {

    private ProductMapper() {}

    public static Product toDomain(ProductJpaEntity entity, List<ProductAttributeJpaEntity> attributes,
                                   List<ProductAttributeOptionJpaEntity> options,
                                   List<ProductImageJpaEntity> images) {
        return Product.reconstitute(
                ProductId.of(entity.getId()),
                entity.getSellerId(),
                CategoryId.of(entity.getCategoryId()),
                entity.getBrandId() == null ? null : BrandId.of(entity.getBrandId()),
                entity.getTitle(),
                entity.getDescription(),
                entity.getStatus(),
                toAttributes(attributes, options),
                toImages(images),
                entity.getPublishedAt(),
                entity.getVersion(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    /** Sản phẩm mới (chưa lưu) có {@code version} null để Hibernate nhận biết bản ghi mới. */
    public static ProductJpaEntity toJpaEntity(Product product) {
        ProductJpaEntity entity = new ProductJpaEntity();
        entity.setId(product.getId().getValue());
        entity.setSellerId(product.getSellerId());
        entity.setCategoryId(product.getCategoryId().getValue());
        entity.setBrandId(product.getBrandId() == null ? null : product.getBrandId().getValue());
        entity.setTitle(product.getTitle());
        entity.setDescription(product.getDescription());
        entity.setStatus(product.getStatus());
        entity.setVersion(product.version());
        entity.setCreatedAt(product.getCreatedAt());
        entity.setUpdatedAt(product.getUpdatedAt());
        entity.setPublishedAt(product.getPublishedAt());
        return entity;
    }

    public static List<ProductAttributeJpaEntity> toAttributeEntities(Product product) {
        UUID productId = product.getId().getValue();
        List<ProductAttributeJpaEntity> rows = new ArrayList<>();
        for (ProductAttribute attribute : product.getAttributes()) {
            ProductAttributeJpaEntity row = new ProductAttributeJpaEntity();
            row.setProductId(productId);
            row.setTemplateId(attribute.templateId().getValue());
            row.setAxis(attribute.axis());
            row.setValue(attribute.value());
            rows.add(row);
        }
        return rows;
    }

    public static List<ProductAttributeOptionJpaEntity> toOptionEntities(Product product) {
        UUID productId = product.getId().getValue();
        List<ProductAttributeOptionJpaEntity> rows = new ArrayList<>();
        for (ProductAttribute attribute : product.getAttributes()) {
            for (AttributeOptionId option : attribute.optionIds()) {
                ProductAttributeOptionJpaEntity row = new ProductAttributeOptionJpaEntity();
                row.setProductId(productId);
                row.setTemplateId(attribute.templateId().getValue());
                row.setOptionId(option.getValue());
                rows.add(row);
            }
        }
        return rows;
    }

    /** Dạng so sánh của danh sách thuộc tính, không phụ thuộc thứ tự: dùng để biết có cần ghi lại hay không. */
    public static Set<ProductAttribute> asSet(List<ProductAttribute> attributes) {
        return new LinkedHashSet<>(attributes);
    }

    public static List<ProductAttribute> toAttributes(List<ProductAttributeJpaEntity> attributes,
                                                       List<ProductAttributeOptionJpaEntity> options) {
        Map<UUID, Set<AttributeOptionId>> optionsByTemplate = new LinkedHashMap<>();
        for (ProductAttributeOptionJpaEntity option : options) {
            optionsByTemplate.computeIfAbsent(option.getTemplateId(), k -> new LinkedHashSet<>())
                    .add(AttributeOptionId.of(option.getOptionId()));
        }
        List<ProductAttribute> result = new ArrayList<>();
        for (ProductAttributeJpaEntity row : attributes) {
            result.add(new ProductAttribute(AttributeTemplateId.of(row.getTemplateId()), row.isAxis(),
                    optionsByTemplate.getOrDefault(row.getTemplateId(), Set.of()), row.getValue()));
        }
        return result;
    }

    public static List<ProductImage> toImages(List<ProductImageJpaEntity> rows) {
        return rows.stream()
                .map(r -> ProductImage.of(ProductImageId.of(r.getId()), r.getObjectKey(), r.getContentType(),
                        r.getSizeBytes(), r.getPosition()))
                .toList();
    }

    public static List<ProductImageJpaEntity> toImageEntities(Product product) {
        UUID productId = product.getId().getValue();
        return product.getImages().stream()
                .map(i -> new ProductImageJpaEntity(i.getId().getValue(), productId, i.getObjectKey(),
                        i.getContentType(), i.getSizeBytes(), i.getPosition()))
                .toList();
    }
}
