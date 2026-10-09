package vn.t3nexus.catalog.presentation.attributetemplate.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import vn.t3nexus.catalog.domain.attributetemplate.InputType;

public record CreateAttributeTemplateRequest(
        @NotBlank String name,
        @NotBlank String displayName,
        @Size(max = 200) String hint,
        @NotNull InputType inputType,
        @Size(max = 20) String unit
) {}
