package vn.t3nexus.catalog.presentation.category.model;

import jakarta.validation.constraints.NotBlank;

public record ReplaceCategoryAttributesRequest(
        @NotBlank String templateId,
        boolean required,
        boolean filterable,
        boolean searchable,
        int displayOrder
) {}
