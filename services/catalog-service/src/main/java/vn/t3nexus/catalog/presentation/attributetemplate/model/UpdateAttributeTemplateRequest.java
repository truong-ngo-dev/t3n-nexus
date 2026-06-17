package vn.t3nexus.catalog.presentation.attributetemplate.model;

import jakarta.validation.constraints.NotBlank;

public record UpdateAttributeTemplateRequest(
        @NotBlank String displayName
) {}
