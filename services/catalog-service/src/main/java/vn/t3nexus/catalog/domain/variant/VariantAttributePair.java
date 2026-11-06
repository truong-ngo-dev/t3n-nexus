package vn.t3nexus.catalog.domain.variant;

import vn.t3nexus.catalog.domain.attributetemplate.AttributeOptionId;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplateId;

/** Một cặp của tổ hợp biến thể: giá trị (tùy chọn) được chọn cho một trục (mẫu thuộc tính). */
public record VariantAttributePair(AttributeTemplateId templateId, AttributeOptionId optionId) {
}
