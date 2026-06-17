package vn.t3nexus.catalog.presentation.attributetemplate.model;

import vn.t3nexus.catalog.domain.attributetemplate.AttributeOptionStatus;

public record AttributeOptionResponse(
        String id,
        String value,
        String displayValue,
        AttributeOptionStatus status
) {}
