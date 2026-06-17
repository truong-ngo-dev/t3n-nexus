package vn.t3nexus.catalog.presentation.category.model;

import jakarta.validation.constraints.NotBlank;

public record UpdateCategoryRequest(
        @NotBlank String name,
        String imageUrl
) {}
