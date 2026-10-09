package vn.t3nexus.catalog.infrastructure.persistence.attributetemplate;

import org.springframework.util.ReflectionUtils;
import vn.t3nexus.catalog.domain.attributetemplate.*;

import java.lang.reflect.Field;
import java.util.List;

public final class AttributeTemplateMapper {

    private AttributeTemplateMapper() {}

    // version nằm ở AbstractAggregateRoot (không có setter công khai) — cùng cách StockMapper (inventory-service).
    private static final Field VERSION_FIELD = ReflectionUtils.findField(AttributeTemplate.class, "version");
    static {
        ReflectionUtils.makeAccessible(VERSION_FIELD);
    }

    public static AttributeTemplate toDomain(AttributeTemplateJpaEntity entity,
                                             List<AttributeOptionJpaEntity> optionEntities) {
        List<AttributeOption> options = optionEntities.stream()
                .map(AttributeTemplateMapper::toOptionDomain)
                .toList();

        AttributeTemplate template = AttributeTemplate.reconstitute(
                AttributeTemplateId.of(entity.getId()),
                entity.getName(),
                entity.getDisplayName(),
                entity.getHint(),
                entity.getInputType(),
                entity.getUnit(),
                entity.getStatus(),
                options,
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
        ReflectionUtils.setField(VERSION_FIELD, template, entity.getVersion());
        return template;
    }

    public static AttributeTemplateJpaEntity toJpaEntity(AttributeTemplate template) {
        AttributeTemplateJpaEntity entity = new AttributeTemplateJpaEntity();
        entity.setId(template.getId().getValue());
        entity.setName(template.getName());
        entity.setDisplayName(template.getDisplayName());
        entity.setHint(template.getHint());
        entity.setInputType(template.getInputType());
        entity.setUnit(template.getUnit());
        entity.setStatus(template.getStatus());
        entity.setVersion((Long) ReflectionUtils.getField(VERSION_FIELD, template));
        entity.setCreatedAt(template.getCreatedAt());
        entity.setUpdatedAt(template.getUpdatedAt());
        return entity;
    }

    public static List<AttributeOptionJpaEntity> toOptionEntities(AttributeTemplate template) {
        String templateId = template.getId().getValue();
        return template.getOptions().stream()
                .map(option -> toOptionJpaEntity(option, templateId))
                .toList();
    }

    private static AttributeOption toOptionDomain(AttributeOptionJpaEntity entity) {
        return AttributeOption.reconstitute(
                AttributeOptionId.of(entity.getId()),
                entity.getValue(),
                entity.getDisplayValue(),
                entity.getStatus(),
                entity.getSortOrder(),
                entity.getCreatedAt()
        );
    }

    private static AttributeOptionJpaEntity toOptionJpaEntity(AttributeOption option, String templateId) {
        AttributeOptionJpaEntity entity = new AttributeOptionJpaEntity();
        entity.setId(option.getId().getValue());
        entity.setTemplateId(templateId);
        entity.setValue(option.getValue());
        entity.setDisplayValue(option.getDisplayValue());
        entity.setStatus(option.getStatus());
        entity.setSortOrder(option.getSortOrder());
        entity.setCreatedAt(option.getCreatedAt());
        return entity;
    }
}
