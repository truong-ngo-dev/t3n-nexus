package vn.t3nexus.catalog.infrastructure.persistence.category;

import tools.jackson.databind.ObjectMapper;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplateId;
import vn.t3nexus.catalog.domain.category.AttributeConstraints;
import vn.t3nexus.catalog.domain.category.Category;
import vn.t3nexus.catalog.domain.category.CategoryAttributeAssignment;
import vn.t3nexus.catalog.domain.category.CategoryId;
import vn.t3nexus.catalog.domain.category.CategoryLevel;

import java.util.List;
import java.util.UUID;

public final class CategoryMapper {

    // Đọc và ghi jsonb ràng buộc: hình cố định (record thuần), không cần cấu hình thêm.
    private static final ObjectMapper JSON = new ObjectMapper();

    private CategoryMapper() {}

    public static Category toDomain(CategoryJpaEntity entity,
                                    List<CategoryAttributeAssignmentJpaEntity> assignmentEntities) {
        CategoryId parentId = entity.getParentId() != null ? CategoryId.of(entity.getParentId()) : null;
        List<CategoryAttributeAssignment> assignments = assignmentEntities.stream()
                .map(CategoryMapper::toAssignmentDomain)
                .toList();
        return Category.reconstitute(
                CategoryId.of(entity.getId()),
                entity.getName(),
                entity.getNameKey(),
                parentId,
                CategoryLevel.of(entity.getLevel()),
                entity.getSortOrder(),
                entity.getStatus(),
                assignments,
                entity.getVersion(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    /** Danh mục mới (chưa lưu) có {@code version} null để Hibernate nhận biết bản ghi mới. */
    public static CategoryJpaEntity toJpaEntity(Category category) {
        CategoryJpaEntity entity = new CategoryJpaEntity();
        entity.setId(category.getId().getValue());
        entity.setName(category.getName());
        entity.setNameKey(category.getNameKey());
        entity.setParentId(category.getParentId() != null ? category.getParentId().getValue() : null);
        entity.setLevel(category.getLevel().getValue());
        entity.setSortOrder(category.getSortOrder());
        entity.setStatus(category.getStatus());
        entity.setVersion(category.version());
        entity.setCreatedAt(category.getCreatedAt());
        entity.setUpdatedAt(category.getUpdatedAt());
        return entity;
    }

    public static List<CategoryAttributeAssignmentJpaEntity> toAssignmentEntities(Category category) {
        UUID categoryId = category.getId().getValue();
        return category.getAssignments().stream()
                .map(a -> toAssignmentJpaEntity(a, categoryId))
                .toList();
    }

    private static CategoryAttributeAssignment toAssignmentDomain(CategoryAttributeAssignmentJpaEntity entity) {
        return CategoryAttributeAssignment.reconstitute(
                AttributeTemplateId.of(entity.getTemplateId()),
                entity.isRequired(),
                entity.getDisplayOrder(),
                JSON.readValue(entity.getConstraintsJson(), AttributeConstraints.class));
    }

    private static CategoryAttributeAssignmentJpaEntity toAssignmentJpaEntity(CategoryAttributeAssignment assignment,
                                                                              UUID categoryId) {
        CategoryAttributeAssignmentJpaEntity entity = new CategoryAttributeAssignmentJpaEntity();
        entity.setCategoryId(categoryId);
        entity.setTemplateId(assignment.getAttributeTemplateId().getValue());
        entity.setRequired(assignment.isRequired());
        entity.setDisplayOrder(assignment.getDisplayOrder());
        entity.setConstraintsJson(JSON.writeValueAsString(assignment.getConstraints()));
        return entity;
    }
}
