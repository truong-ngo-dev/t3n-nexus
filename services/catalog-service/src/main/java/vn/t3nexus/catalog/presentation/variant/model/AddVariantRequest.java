package vn.t3nexus.catalog.presentation.variant.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record AddVariantRequest(
        @NotEmpty @Valid List<CombinationItem> combination,
        String skuCode,
        @Min(1) long price
) {
    public record CombinationItem(@NotBlank String templateId, @NotBlank String optionId) {}
}
