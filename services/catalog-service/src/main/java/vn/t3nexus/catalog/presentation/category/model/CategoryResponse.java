package vn.t3nexus.catalog.presentation.category.model;

import vn.t3nexus.catalog.application.category.CategoryView;
import vn.t3nexus.catalog.domain.category.CategoryStatus;

import java.util.UUID;

/** Danh mục sau lệnh ghi, kèm phiên bản sau thay đổi. */
public record CategoryResponse(UUID id, UUID parentId, int level, String name, int sortOrder, CategoryStatus status,
                               long version) {

    public static CategoryResponse from(CategoryView view) {
        return new CategoryResponse(view.id(), view.parentId(), view.level(), view.name(), view.sortOrder(),
                view.status(), view.version());
    }
}
