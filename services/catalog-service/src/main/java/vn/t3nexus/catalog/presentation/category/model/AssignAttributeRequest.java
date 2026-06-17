package vn.t3nexus.catalog.presentation.category.model;

import jakarta.validation.constraints.NotBlank;

public record AssignAttributeRequest(
        @NotBlank String templateId,
        boolean variantDefining,
        boolean required,
        boolean filterable,
        int displayOrder
) {}
