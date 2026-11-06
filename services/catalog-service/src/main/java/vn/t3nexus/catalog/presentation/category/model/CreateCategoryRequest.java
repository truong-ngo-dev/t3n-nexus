package vn.t3nexus.catalog.presentation.category.model;

import java.util.UUID;

/** Tạo danh mục; {@code parentId} rỗng là cấp một. Quy tắc nội dung (rỗng, độ dài) kiểm ở miền: 400 {@code CATALOG_FIELD_INVALID}. */
public record CreateCategoryRequest(String name, UUID parentId) {}
