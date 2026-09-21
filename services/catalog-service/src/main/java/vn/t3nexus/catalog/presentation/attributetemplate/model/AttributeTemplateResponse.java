package vn.t3nexus.catalog.presentation.attributetemplate.model;

import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplateStatus;
import vn.t3nexus.catalog.domain.attributetemplate.InputType;

import java.util.List;

public record AttributeTemplateResponse(
        String id,
        String name,
        String displayName,
        InputType inputType,
        AttributeTemplateStatus status,
        List<AttributeOptionResponse> options
) {}
