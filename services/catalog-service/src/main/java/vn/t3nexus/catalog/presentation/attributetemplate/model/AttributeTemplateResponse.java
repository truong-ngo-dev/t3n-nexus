package vn.t3nexus.catalog.presentation.attributetemplate.model;

import vn.t3nexus.catalog.application.attributetemplate.TemplateView;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeOptionStatus;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplateStatus;
import vn.t3nexus.catalog.domain.attributetemplate.InputType;

import java.util.List;
import java.util.UUID;

/** Mẫu kèm mọi tùy chọn và phiên bản (sau thay đổi, với lệnh ghi). */
public record AttributeTemplateResponse(
        UUID id,
        String name,
        String displayName,
        String hint,
        InputType inputType,
        String unit,
        AttributeTemplateStatus status,
        boolean usable,
        long version,
        List<Option> options
) {

    public record Option(UUID id, String value, String displayValue, AttributeOptionStatus status, int sortOrder) {}

    public static AttributeTemplateResponse from(TemplateView v) {
        List<Option> options = v.options().stream()
                .map(o -> new Option(o.id(), o.value(), o.displayValue(), o.status(), o.sortOrder()))
                .toList();
        return new AttributeTemplateResponse(v.id(), v.name(), v.displayName(), v.hint(), v.inputType(), v.unit(),
                v.status(), v.usable(), v.version(), options);
    }
}
