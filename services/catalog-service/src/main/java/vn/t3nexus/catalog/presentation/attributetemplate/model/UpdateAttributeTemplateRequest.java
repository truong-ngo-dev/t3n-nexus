package vn.t3nexus.catalog.presentation.attributetemplate.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateAttributeTemplateRequest(
        @NotBlank String displayName,
        @Size(max = 200) String hint
) {}
