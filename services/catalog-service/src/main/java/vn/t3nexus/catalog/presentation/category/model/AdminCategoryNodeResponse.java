package vn.t3nexus.catalog.presentation.category.model;

import vn.t3nexus.catalog.application.category.GetAdminCategoryTree;
import vn.t3nexus.catalog.domain.category.CategoryStatus;

import java.util.List;
import java.util.UUID;

/** Nút của cây đầy đủ cho quản trị: mọi trạng thái, kèm cờ dùng được và phiên bản. */
public record AdminCategoryNodeResponse(UUID id, String name, int level, int sortOrder, CategoryStatus status,
                                        boolean usable, long version, List<AdminCategoryNodeResponse> children) {

    public static AdminCategoryNodeResponse from(GetAdminCategoryTree.Node node) {
        return new AdminCategoryNodeResponse(node.id(), node.name(), node.level(), node.sortOrder(), node.status(),
                node.usable(), node.version(), node.children().stream().map(AdminCategoryNodeResponse::from).toList());
    }
}
