package vn.t3nexus.catalog.application.category;

import vn.t3nexus.catalog.domain.category.Category;
import vn.t3nexus.catalog.domain.category.CategoryStatus;

import java.util.UUID;

/** Danh mục trả về sau lệnh ghi (kèm phiên bản sau thay đổi). */
public record CategoryView(UUID id, UUID parentId, int level, String name, int sortOrder, CategoryStatus status,
                           long version) {

    public static CategoryView of(Category category, long version) {
        return new CategoryView(
                category.getId().getValue(),
                category.getParentId() == null ? null : category.getParentId().getValue(),
                category.getLevel().getValue(),
                category.getName(),
                category.getSortOrder(),
                category.getStatus(),
                version);
    }
}
