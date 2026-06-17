package vn.t3nexus.catalog.presentation.brand.model;

import jakarta.validation.constraints.NotBlank;

public record UpdateBrandRequest(
        @NotBlank String name
) {}
