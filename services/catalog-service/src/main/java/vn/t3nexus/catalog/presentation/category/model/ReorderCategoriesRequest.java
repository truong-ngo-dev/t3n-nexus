package vn.t3nexus.catalog.presentation.category.model;

import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

/** Thứ tự mới của các anh em cùng cha; {@code parentId} rỗng là các danh mục cấp một. */
public record ReorderCategoriesRequest(UUID parentId, @NotNull List<UUID> categoryIds) {}
