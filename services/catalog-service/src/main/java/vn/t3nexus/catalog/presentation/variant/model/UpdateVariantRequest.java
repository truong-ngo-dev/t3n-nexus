package vn.t3nexus.catalog.presentation.variant.model;

import jakarta.validation.constraints.Min;

public record UpdateVariantRequest(
        @Min(1) long price,
        String skuCode
) {}
