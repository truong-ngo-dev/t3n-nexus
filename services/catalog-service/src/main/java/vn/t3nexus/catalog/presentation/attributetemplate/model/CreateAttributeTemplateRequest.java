package vn.t3nexus.catalog.presentation.attributetemplate.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeScope;
import vn.t3nexus.catalog.domain.attributetemplate.InputType;

public record CreateAttributeTemplateRequest(
        @NotBlank String name,
        @NotBlank String displayName,
        @NotNull InputType inputType,
        @NotNull AttributeScope scope
) {}
