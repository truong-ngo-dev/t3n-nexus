package vn.t3nexus.catalog.presentation.category.model;

import jakarta.validation.constraints.NotBlank;

public record CreateCategoryRequest(
        @NotBlank String name,
        String parentId
) {}
