package vn.t3nexus.catalog.presentation.category.model;

import vn.t3nexus.catalog.application.category.GetCategoryAttributes;
import vn.t3nexus.catalog.domain.attributetemplate.InputType;
import vn.t3nexus.catalog.domain.category.AttributeConstraints;

import java.util.List;
import java.util.UUID;

/** Thuộc tính người bán dùng ở một danh mục lá: chỉ mẫu dùng được, chỉ tùy chọn đang dùng. */
public record CategoryAttributeResponse(UUID templateId, String name, String displayName, String hint,
                                        InputType inputType, String unit, boolean required,
                                        AttributeConstraints constraints, int displayOrder, List<Option> options) {

    public record Option(UUID id, String value, String displayValue) {}

    public static CategoryAttributeResponse from(GetCategoryAttributes.Attribute a) {
        return new CategoryAttributeResponse(a.templateId(), a.name(), a.displayName(), a.hint(), a.inputType(),
                a.unit(), a.required(), a.constraints(), a.displayOrder(),
                a.options().stream().map(o -> new Option(o.id(), o.value(), o.displayValue())).toList());
    }
}
