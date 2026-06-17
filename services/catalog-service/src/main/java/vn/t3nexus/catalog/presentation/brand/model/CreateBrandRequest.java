package vn.t3nexus.catalog.presentation.brand.model;

import jakarta.validation.constraints.NotBlank;

public record CreateBrandRequest(
        @NotBlank String name,
        @NotBlank String slug
) {}
