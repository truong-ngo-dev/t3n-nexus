package vn.t3nexus.catalog.presentation.attributetemplate.model;

import jakarta.validation.constraints.NotBlank;

public record UpdateAttributeOptionRequest(@NotBlank String displayValue) {}
