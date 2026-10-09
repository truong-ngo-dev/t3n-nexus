package vn.t3nexus.catalog.presentation.category.model;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/** {@code parentId} null = sắp lại các danh mục gốc (L1). Phải chứa MỌI anh em cùng cha, mỗi cái đúng 1 lần. */
public record ReorderCategoriesRequest(String parentId, @NotEmpty List<String> categoryIds) {}
