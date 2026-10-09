package vn.t3nexus.catalog.presentation.attributetemplate.model;

import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplateStatus;
import vn.t3nexus.catalog.domain.attributetemplate.InputType;

import java.util.List;

/** Chi tiết template (admin): đủ thuộc tính + mọi option theo thứ tự chuẩn. */
public record AttributeTemplateResponse(
        String id,
        String name,
        String displayName,
        String hint,
        InputType inputType,
        String unit,
        AttributeTemplateStatus status,
        boolean usable,
        List<AttributeOptionResponse> options
) {}
