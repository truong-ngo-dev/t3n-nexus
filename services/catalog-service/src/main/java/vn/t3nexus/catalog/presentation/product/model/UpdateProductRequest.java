package vn.t3nexus.catalog.presentation.product.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record UpdateProductRequest(
        @NotBlank String name,
        String description,
        Integer warrantyMonths,
        String warrantyType,
        String warrantyCoverage,
        @NotNull @Valid List<CreateProductRequest.AttributeValueItem> attributeValues
) {}
