package vn.t3nexus.catalog.infrastructure.persistence.category;

import tools.jackson.databind.ObjectMapper;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplateId;
import vn.t3nexus.catalog.domain.category.*;

import java.util.List;

public final class CategoryMapper {

    // Đọc/ghi jsonb constraints/discovery — shape cố định (record thuần), không cần cấu hình gì thêm
    // (đã kiểm chứng round-trip với record lồng enum/record/null field).
    private static final ObjectMapper JSON = new ObjectMapper();

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
                entity.getSortOrder(),
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
        entity.setSortOrder(category.getSortOrder());
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

    private static CategoryAttributeAssignment toAssignmentDomain(CategoryAttributeAssignmentJpaEntity entity) {
        return CategoryAttributeAssignment.reconstitute(
                AttributeTemplateId.of(entity.getTemplateId()),
                entity.isRequired(),
                entity.getDisplayOrder(),
                JSON.readValue(entity.getConstraintsJson(), AttributeConstraints.class),
                JSON.readValue(entity.getDiscoveryJson(), Discovery.class)
        );
    }

    private static CategoryAttributeAssignmentJpaEntity toAssignmentJpaEntity(
            CategoryAttributeAssignment assignment, String categoryId) {
        CategoryAttributeAssignmentJpaEntity entity = new CategoryAttributeAssignmentJpaEntity();
        entity.setCategoryId(categoryId);
        entity.setTemplateId(assignment.getAttributeTemplateId().getValue());
        entity.setRequired(assignment.isRequired());
        entity.setDisplayOrder(assignment.getDisplayOrder());
        entity.setConstraintsJson(JSON.writeValueAsString(assignment.getConstraints()));
        entity.setDiscoveryJson(JSON.writeValueAsString(assignment.getDiscovery()));
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
