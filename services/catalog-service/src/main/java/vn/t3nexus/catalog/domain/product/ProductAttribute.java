package vn.t3nexus.catalog.domain.product;

import vn.t3nexus.catalog.domain.attributetemplate.AttributeOptionId;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplateId;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Giá trị thuộc tính đã chuẩn hóa của một sản phẩm, một dòng cho mỗi mẫu. Kiểu chọn: tập mã tùy chọn ({@code value} rỗng);
 * các kiểu còn lại: một chuỗi chuẩn hóa ({@code optionIds} rỗng). {@code axis}: làm trục biến thể; với dòng trục,
 * {@code optionIds} chính là tập giá trị của trục.
 */
public record ProductAttribute(AttributeTemplateId templateId, boolean axis, Set<AttributeOptionId> optionIds,
                               String value) {

    public ProductAttribute {
        Objects.requireNonNull(templateId, "templateId");
        optionIds = Collections.unmodifiableSet(new LinkedHashSet<>(optionIds == null ? Set.of() : optionIds));
        if (value != null && !optionIds.isEmpty()) {
            throw new IllegalArgumentException("Một thuộc tính không thể vừa có tùy chọn vừa có giá trị rời");
        }
        if (axis && value != null) {
            throw new IllegalArgumentException("Thuộc tính làm trục là kiểu chọn, không có giá trị rời");
        }
    }

    public static ProductAttribute ofOptions(AttributeTemplateId templateId, boolean axis,
                                             Collection<AttributeOptionId> optionIds) {
        return new ProductAttribute(templateId, axis, new LinkedHashSet<>(optionIds), null);
    }

    public static ProductAttribute ofValue(AttributeTemplateId templateId, String value) {
        return new ProductAttribute(templateId, false, Set.of(), Objects.requireNonNull(value, "value"));
    }
}
