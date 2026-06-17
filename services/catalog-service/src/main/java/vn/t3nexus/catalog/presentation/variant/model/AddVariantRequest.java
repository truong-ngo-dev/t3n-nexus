package vn.t3nexus.catalog.presentation.variant.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record AddVariantRequest(
        @NotEmpty @Valid List<CombinationItem> combination,
        String skuCode,
        @Min(1) long price,
        @Min(0) int stock
) {
    public record CombinationItem(@NotBlank String templateId, @NotBlank String optionId) {}
}
