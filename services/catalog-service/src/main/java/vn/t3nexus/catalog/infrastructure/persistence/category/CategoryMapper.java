package vn.t3nexus.catalog.infrastructure.persistence.category;

import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplateId;
import vn.t3nexus.catalog.domain.category.*;

import java.util.List;

public final class CategoryMapper {

    private CategoryMapper() {}

    public static Category toDomain(CategoryJpaEntity entity,
                                    List<CategoryAttributeAssignmentJpaEntity> assignmentEntities) {
        CategoryId parentId = entity.getParentId() != null
                ? CategoryId.of(entity.getParentId())
                : null;

        List<CategoryAttributeAssignment> assignments = assignmentEntities.stream()
                .map(CategoryMapper::toAssignmentDomain)
                .toList();

        return Category.reconstitute(
                CategoryId.of(entity.getId()),
                entity.getName(),
                entity.getSlug(),
                parentId,
                fromInt(entity.getLevel()),
                entity.getImageUrl(),
                entity.getStatus(),
                assignments,
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    public static CategoryJpaEntity toJpaEntity(Category category) {
        CategoryJpaEntity entity = new CategoryJpaEntity();
        entity.setId(category.getId().getValue());
        entity.setName(category.getName());
        entity.setSlug(category.getSlug());
        entity.setParentId(category.getParentId() != null ? category.getParentId().getValue() : null);
        entity.setLevel(category.getLevel().getValue());
        entity.setImageUrl(category.getImageUrl());
        entity.setStatus(category.getStatus());
        entity.setCreatedAt(category.getCreatedAt());
        entity.setUpdatedAt(category.getUpdatedAt());
        return entity;
    }

    public static List<CategoryAttributeAssignmentJpaEntity> toAssignmentEntities(Category category) {
        String categoryId = category.getId().getValue();
        return category.getAssignments().stream()
                .map(a -> toAssignmentJpaEntity(a, categoryId))
                .toList();
    }

    private static CategoryAttributeAssignment toAssignmentDomain(
            CategoryAttributeAssignmentJpaEntity entity) {
        return new CategoryAttributeAssignment(
                AttributeTemplateId.of(entity.getTemplateId()),
                entity.isVariantDefining(),
                entity.isRequired(),
                entity.isFilterable(),
                entity.getDisplayOrder()
        );
    }

    private static CategoryAttributeAssignmentJpaEntity toAssignmentJpaEntity(
            CategoryAttributeAssignment assignment, String categoryId) {
        CategoryAttributeAssignmentJpaEntity entity = new CategoryAttributeAssignmentJpaEntity();
        entity.setCategoryId(categoryId);
        entity.setTemplateId(assignment.getAttributeTemplateId().getValue());
        entity.setVariantDefining(assignment.isVariantDefining());
        entity.setRequired(assignment.isRequired());
        entity.setFilterable(assignment.isFilterable());
        entity.setDisplayOrder(assignment.getDisplayOrder());
        return entity;
    }

    private static CategoryLevel fromInt(int level) {
        return switch (level) {
            case 1 -> CategoryLevel.L1;
            case 2 -> CategoryLevel.L2;
            case 3 -> CategoryLevel.L3;
            default -> throw new IllegalArgumentException("Invalid category level: " + level);
        };
    }
}
