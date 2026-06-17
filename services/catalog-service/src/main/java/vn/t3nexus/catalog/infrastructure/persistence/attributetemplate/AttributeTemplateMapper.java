package vn.t3nexus.catalog.infrastructure.persistence.attributetemplate;

import vn.t3nexus.catalog.domain.attributetemplate.*;

import java.util.List;

public final class AttributeTemplateMapper {

    private AttributeTemplateMapper() {}

    public static AttributeTemplate toDomain(AttributeTemplateJpaEntity entity,
                                              List<AttributeOptionJpaEntity> optionEntities) {
        List<AttributeOption> options = optionEntities.stream()
                .map(AttributeTemplateMapper::toOptionDomain)
                .toList();

        return AttributeTemplate.reconstitute(
                AttributeTemplateId.of(entity.getId()),
                entity.getName(),
                entity.getDisplayName(),
                entity.getInputType(),
                entity.getScope(),
                options,
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    public static AttributeTemplateJpaEntity toJpaEntity(AttributeTemplate template) {
        AttributeTemplateJpaEntity entity = new AttributeTemplateJpaEntity();
        entity.setId(template.getId().getValue());
        entity.setName(template.getName());
        entity.setDisplayName(template.getDisplayName());
        entity.setInputType(template.getInputType());
        entity.setScope(template.getScope());
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
        entity.setCreatedAt(option.getCreatedAt());
        return entity;
    }
}
