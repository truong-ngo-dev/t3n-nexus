package vn.t3nexus.catalog.infrastructure.persistence.variant;

import vn.t3nexus.catalog.domain.attributetemplate.AttributeOptionId;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplateId;
import vn.t3nexus.catalog.domain.variant.*;

import java.util.List;

public final class VariantMapper {

    private VariantMapper() {}

    public static Variant toDomain(VariantJpaEntity entity,
                                   List<VariantCombinationItemJpaEntity> combinationItems,
                                   List<SkuImageJpaEntity> imageEntities) {
        List<VariantAttributePair> pairs = combinationItems.stream()
                .map(item -> new VariantAttributePair(
                        AttributeTemplateId.of(item.getTemplateId()),
                        AttributeOptionId.of(item.getOptionId())))
                .toList();

        List<SkuImage> images = imageEntities.stream()
                .map(i -> SkuImage.reconstitute(
                        SkuImageId.of(i.getId()),
                        i.getObjectKey(),
                        i.getDisplayOrder()))
                .toList();

        return Variant.reconstitute(
                VariantId.of(entity.getId()),
                entity.getProductId(),
                new VariantCombination(pairs),
                entity.getSkuCode(),
                entity.getPrice(),
                entity.getStock(),
                entity.getStatus(),
                images,
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    public static VariantJpaEntity toJpaEntity(Variant variant) {
        VariantJpaEntity entity = new VariantJpaEntity();
        entity.setId(variant.getId().getValue());
        entity.setProductId(variant.getProductId());
        entity.setCombinationHash(variant.getCombination().toHash());
        entity.setSkuCode(variant.getSkuCode());
        entity.setPrice(variant.getPrice());
        entity.setStock(variant.getStock());
        entity.setStatus(variant.getStatus());
        entity.setCreatedAt(variant.getCreatedAt());
        entity.setUpdatedAt(variant.getUpdatedAt());
        return entity;
    }

    public static List<VariantCombinationItemJpaEntity> toCombinationItems(Variant variant) {
        String variantId = variant.getId().getValue();
        return variant.getCombination().getPairs().stream()
                .map(pair -> {
                    VariantCombinationItemJpaEntity item = new VariantCombinationItemJpaEntity();
                    item.setVariantId(variantId);
                    item.setTemplateId(pair.templateId().getValue());
                    item.setOptionId(pair.optionId().getValue());
                    return item;
                })
                .toList();
    }

    public static List<SkuImageJpaEntity> toSkuImageEntities(Variant variant) {
        String variantId = variant.getId().getValue();
        return variant.getImages().stream()
                .map(img -> {
                    SkuImageJpaEntity e = new SkuImageJpaEntity();
                    e.setId(img.getId().getValue());
                    e.setVariantId(variantId);
                    e.setObjectKey(img.getObjectKey());
                    e.setDisplayOrder(img.getDisplayOrder());
                    return e;
                })
                .toList();
    }
}
