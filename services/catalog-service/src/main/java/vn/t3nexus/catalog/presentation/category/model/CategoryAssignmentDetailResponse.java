package vn.t3nexus.catalog.presentation.category.model;

import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplateStatus;
import vn.t3nexus.catalog.domain.attributetemplate.InputType;
import vn.t3nexus.catalog.domain.category.AttributeConstraints;
import vn.t3nexus.catalog.domain.category.Discovery;

/** Chi tiết quản trị 1 assignment — ĐỦ, kể cả template đã tắt (khác {@link CategoryAttributeResponse}). */
public record CategoryAssignmentDetailResponse(
        String templateId,
        String name,
        String displayName,
        InputType inputType,
        AttributeTemplateStatus templateStatus,
        boolean required,
        int displayOrder,
        AttributeConstraints constraints,
        Discovery discovery
) {}
