package vn.t3nexus.catalog.presentation.category.model;

import vn.t3nexus.catalog.application.category.AssignmentView;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplateStatus;
import vn.t3nexus.catalog.domain.attributetemplate.InputType;
import vn.t3nexus.catalog.domain.category.AttributeConstraints;

import java.util.UUID;

/** Một dòng gán cho quản trị: kèm thông tin mẫu và cờ dùng được, kể cả khi mẫu nay đã ngừng. */
public record AdminAssignmentResponse(UUID templateId, String name, String displayName, InputType inputType,
                                      AttributeTemplateStatus templateStatus, boolean usable, boolean required,
                                      int displayOrder, AttributeConstraints constraints) {

    public static AdminAssignmentResponse from(AssignmentView v) {
        return new AdminAssignmentResponse(v.templateId(), v.name(), v.displayName(), v.inputType(),
                v.templateStatus(), v.usable(), v.required(), v.displayOrder(), v.constraints());
    }
}
