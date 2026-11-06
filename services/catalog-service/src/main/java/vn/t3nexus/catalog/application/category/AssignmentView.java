package vn.t3nexus.catalog.application.category;

import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplate;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplateStatus;
import vn.t3nexus.catalog.domain.attributetemplate.InputType;
import vn.t3nexus.catalog.domain.category.AttributeConstraints;
import vn.t3nexus.catalog.domain.category.CategoryAttributeAssignment;

import java.util.UUID;

/** Một dòng gán cho quản trị: kèm thông tin mẫu và cờ "dùng được", kể cả khi mẫu nay đã ngừng. */
public record AssignmentView(UUID templateId, String name, String displayName, InputType inputType,
                             AttributeTemplateStatus templateStatus, boolean usable, boolean required,
                             int displayOrder, AttributeConstraints constraints) {

    public static AssignmentView of(CategoryAttributeAssignment assignment, AttributeTemplate template) {
        return new AssignmentView(template.getId().getValue(), template.getName(), template.getDisplayName(),
                template.getInputType(), template.getStatus(), template.isUsable(), assignment.isRequired(),
                assignment.getDisplayOrder(), assignment.getConstraints());
    }
}
