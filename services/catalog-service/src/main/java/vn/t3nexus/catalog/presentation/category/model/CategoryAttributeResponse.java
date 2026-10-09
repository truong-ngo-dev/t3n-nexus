package vn.t3nexus.catalog.presentation.category.model;

import vn.t3nexus.catalog.domain.attributetemplate.InputType;
import vn.t3nexus.catalog.domain.category.AttributeConstraints;

import java.util.List;

/** Thuộc tính seller được dùng ở 1 danh mục lá — chỉ template dùng được, option ACTIVE theo thứ tự chuẩn.
 *  {@code constraints} shape thay đổi theo {@code inputType} (AGG-CAT-01) — field nào không áp dụng thì null. */
public record CategoryAttributeResponse(
        String templateId,
        String name,
        String displayName,
        String hint,
        InputType inputType,
        String unit,
        boolean required,
        AttributeConstraints constraints,
        int displayOrder,
        List<Option> options
) {
    public record Option(String id, String value, String displayValue) {}
}
