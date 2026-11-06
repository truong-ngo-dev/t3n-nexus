package vn.t3nexus.catalog.infrastructure.persistence.variant;

import vn.t3nexus.catalog.domain.attributetemplate.AttributeOptionId;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplateId;
import vn.t3nexus.catalog.domain.product.ProductId;
import vn.t3nexus.catalog.domain.variant.Variant;
import vn.t3nexus.catalog.domain.variant.VariantAttributePair;
import vn.t3nexus.catalog.domain.variant.VariantId;

import java.util.List;

public final class VariantMapper {

    private VariantMapper() {}

    public static Variant toDomain(VariantJpaEntity entity, List<VariantCombinationItemJpaEntity> items) {
        List<VariantAttributePair> combination = items.stream()
                .map(i -> new VariantAttributePair(AttributeTemplateId.of(i.getTemplateId()),
                        AttributeOptionId.of(i.getOptionId())))
                .toList();
        return Variant.reconstitute(VariantId.of(entity.getId()), ProductId.of(entity.getProductId()),
                entity.getSellerId(), combination, entity.getPrice(), entity.getSku(), entity.getStatus(),
                entity.getCreatedAt(), entity.getUpdatedAt());
    }

    public static VariantJpaEntity toJpaEntity(Variant variant) {
        VariantJpaEntity entity = new VariantJpaEntity();
        entity.setId(variant.getId().getValue());
        entity.setProductId(variant.getProductId().getValue());
        entity.setSellerId(variant.getSellerId());
        entity.setCombinationKey(variant.getCombinationKey());
        entity.setPrice(variant.getPrice());
        entity.setSku(variant.getSku());
        entity.setStatus(variant.getStatus());
        entity.setCreatedAt(variant.getCreatedAt());
        entity.setUpdatedAt(variant.getUpdatedAt());
        return entity;
    }

    public static List<VariantCombinationItemJpaEntity> toItems(Variant variant) {
        return variant.getCombination().stream().map(pair -> {
            VariantCombinationItemJpaEntity item = new VariantCombinationItemJpaEntity();
            item.setVariantId(variant.getId().getValue());
            item.setTemplateId(pair.templateId().getValue());
            item.setOptionId(pair.optionId().getValue());
            return item;
        }).toList();
    }
}
