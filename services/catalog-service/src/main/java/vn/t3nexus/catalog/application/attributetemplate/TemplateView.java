package vn.t3nexus.catalog.application.attributetemplate;

import vn.t3nexus.catalog.domain.attributetemplate.AttributeOption;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeOptionStatus;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplate;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplateStatus;
import vn.t3nexus.catalog.domain.attributetemplate.InputType;

import java.util.List;
import java.util.UUID;

/** Mẫu thuộc tính kèm mọi tùy chọn, trả về sau lệnh ghi (kèm phiên bản sau thay đổi) và cho đọc chi tiết. */
public record TemplateView(
        UUID id,
        String name,
        String displayName,
        String hint,
        InputType inputType,
        String unit,
        AttributeTemplateStatus status,
        boolean usable,
        long version,
        List<OptionView> options
) {

    public record OptionView(UUID id, String value, String displayValue, AttributeOptionStatus status, int sortOrder) {}

    public static TemplateView of(AttributeTemplate template, long version) {
        List<OptionView> options = template.getOptionsInOrder().stream().map(TemplateView::option).toList();
        return new TemplateView(template.getId().getValue(), template.getName(), template.getDisplayName(),
                template.getHint(), template.getInputType(), template.getUnit(), template.getStatus(),
                template.isUsable(), version, options);
    }

    private static OptionView option(AttributeOption o) {
        return new OptionView(o.getId().getValue(), o.getValue(), o.getDisplayValue(), o.getStatus(), o.getSortOrder());
    }
}
