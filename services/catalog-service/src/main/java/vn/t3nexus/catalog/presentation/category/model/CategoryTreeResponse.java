package vn.t3nexus.catalog.presentation.category.model;

import vn.t3nexus.catalog.application.category.GetCategoryTree;

import java.util.List;
import java.util.UUID;

/** Nút của cây để chọn: chỉ nhánh dùng được. */
public record CategoryTreeResponse(UUID id, String name, int level, List<CategoryTreeResponse> children) {

    public static CategoryTreeResponse from(GetCategoryTree.Node node) {
        return new CategoryTreeResponse(node.id(), node.name(), node.level(),
                node.children().stream().map(CategoryTreeResponse::from).toList());
    }
}
