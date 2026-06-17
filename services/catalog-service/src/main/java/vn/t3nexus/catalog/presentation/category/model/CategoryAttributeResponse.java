package vn.t3nexus.catalog.presentation.category.model;

import vn.t3nexus.catalog.domain.attributetemplate.AttributeScope;
import vn.t3nexus.catalog.domain.attributetemplate.InputType;

public record CategoryAttributeResponse(
        String templateId,
        String name,
        String displayName,
        InputType inputType,
        AttributeScope scope,
        boolean variantDefining,
        boolean required,
        boolean filterable,
        int displayOrder
) {}
