package vn.t3nexus.catalog.presentation.category.model;

import vn.t3nexus.catalog.domain.category.CategoryStatus;

import java.util.List;

/** Cây quản trị — mọi trạng thái, kèm cờ để admin bật lại danh mục đã tắt. */
public record CategoryAdminTreeResponse(
        String id,
        String name,
        String slug,
        int level,
        String imageUrl,
        CategoryStatus status,
        List<CategoryAdminTreeResponse> children
) {}
