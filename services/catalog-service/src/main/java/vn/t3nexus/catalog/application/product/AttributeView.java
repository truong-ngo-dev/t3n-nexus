package vn.t3nexus.catalog.application.product;

import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplate;
import vn.t3nexus.catalog.domain.product.ProductAttribute;

import java.util.List;
import java.util.UUID;

/**
 * Một thuộc tính của sản phẩm khi trả về: tên hiển thị hiện tại của mẫu, cờ trục, các tùy chọn đã chọn (tên lấy từ mẫu tại
 * lúc đọc nên đổi tên có hiệu lực ngay; tùy chọn đã ngừng vẫn trả, {@code usable = false}) hoặc giá trị rời.
 */
public record AttributeView(UUID templateId, String name, boolean axis, List<OptionView> options, String value) {

    public record OptionView(UUID id, String displayValue, boolean usable) {
    }

    static AttributeView of(ProductAttribute attribute, AttributeTemplate template) {
        List<OptionView> options = template.getOptionsInOrder().stream()
                .filter(o -> attribute.optionIds().contains(o.getId()))
                .map(o -> new OptionView(o.getId().getValue(), o.getDisplayValue(), o.isActive()))
                .toList();
        return new AttributeView(attribute.templateId().getValue(), template.getDisplayName(), attribute.axis(),
                options, attribute.value());
    }
}
