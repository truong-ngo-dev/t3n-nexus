package vn.t3nexus.catalog.domain.product;

import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplateId;

import java.util.List;

/**
 * Giá trị thuộc tính người bán gửi lên, chưa kiểm. Kiểu chọn: {@code values} là mã tùy chọn (dạng UUID chữ); các kiểu còn
 * lại: đúng một chuỗi. {@code axis}: thuộc tính này làm trục biến thể (INV-CAT-33).
 */
public record AttributeInput(AttributeTemplateId templateId, List<String> values, boolean axis) {
}
