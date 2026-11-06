package vn.t3nexus.catalog.domain.category;

import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplate;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplateId;
import vn.t3nexus.catalog.domain.attributetemplate.InputType;
import vn.t3nexus.lib.common.domain.exception.DomainException;
import vn.t3nexus.lib.common.domain.model.ValueObject;

import java.util.Objects;

/**
 * Cách một mẫu được dùng ở một danh mục lá (AGG-CAT-01). Bằng nhau chỉ theo mã mẫu: hai dòng cùng mẫu là trùng lặp,
 * bất kể cấu hình khác. {@code constraints} là ràng buộc riêng của ngành hàng, chồng lên kiểm định dạng mặc định của
 * mẫu; trường nào không thuộc kiểu nhập của mẫu thì bị từ chối.
 */
public class CategoryAttributeAssignment implements ValueObject {

    private final AttributeTemplateId attributeTemplateId;
    private final boolean required;
    private final int displayOrder;
    private final AttributeConstraints constraints;

    private CategoryAttributeAssignment(AttributeTemplateId attributeTemplateId, boolean required, int displayOrder,
                                        AttributeConstraints constraints) {
        this.attributeTemplateId = attributeTemplateId;
        this.required            = required;
        this.displayOrder        = displayOrder;
        this.constraints         = constraints;
    }

    /** Tạo mới: kiểm {@code constraints} hợp với kiểu nhập của mẫu; null thì coi là không ràng buộc. */
    public static CategoryAttributeAssignment create(AttributeTemplateId templateId, InputType inputType,
                                                     boolean required, int displayOrder,
                                                     AttributeConstraints constraints) {
        AttributeConstraints c = constraints == null ? AttributeConstraints.NONE : constraints;
        validateConstraints(inputType, c);
        return new CategoryAttributeAssignment(templateId, required, displayOrder, c);
    }

    /** Đọc lại từ lưu trữ: dữ liệu đã qua {@link #create} lúc ghi, không kiểm lại. */
    public static CategoryAttributeAssignment reconstitute(AttributeTemplateId templateId, boolean required,
                                                           int displayOrder, AttributeConstraints constraints) {
        return new CategoryAttributeAssignment(templateId, required, displayOrder,
                constraints == null ? AttributeConstraints.NONE : constraints);
    }

    // ───────────── Kiểm ràng buộc theo kiểu nhập ─────────────

    private static void validateConstraints(InputType type, AttributeConstraints c) {
        switch (type) {
            case SINGLE_SELECT, BOOLEAN -> {
                if (!AttributeConstraints.NONE.equals(c)) {
                    throw invalid(type + " không có ràng buộc");
                }
            }
            case TEXT -> {
                requireNull(c.min(), "min", type);
                requireNull(c.max(), "max", type);
                requireNull(c.integerOnly(), "integerOnly", type);
                requireNull(c.inputPrecision(), "inputPrecision", type);
                requireNull(c.minDate(), "minDate", type);
                requireNull(c.maxDate(), "maxDate", type);
                requireNull(c.maxSelections(), "maxSelections", type);
                if (c.maxLength() != null && (c.maxLength() < 1 || c.maxLength() > AttributeTemplate.TEXT_MAX_LENGTH)) {
                    throw invalid("maxLength phải trong khoảng 1.." + AttributeTemplate.TEXT_MAX_LENGTH);
                }
            }
            case NUMBER -> {
                requireNull(c.maxLength(), "maxLength", type);
                requireNull(c.inputPrecision(), "inputPrecision", type);
                requireNull(c.minDate(), "minDate", type);
                requireNull(c.maxDate(), "maxDate", type);
                requireNull(c.maxSelections(), "maxSelections", type);
                if (c.min() != null && c.max() != null && c.min().compareTo(c.max()) > 0) {
                    throw invalid("min phải nhỏ hơn hoặc bằng max");
                }
            }
            case MULTI_SELECT -> {
                requireNull(c.maxLength(), "maxLength", type);
                requireNull(c.min(), "min", type);
                requireNull(c.max(), "max", type);
                requireNull(c.integerOnly(), "integerOnly", type);
                requireNull(c.inputPrecision(), "inputPrecision", type);
                requireNull(c.minDate(), "minDate", type);
                requireNull(c.maxDate(), "maxDate", type);
                if (c.maxSelections() != null && c.maxSelections() < 1) {
                    throw invalid("maxSelections phải từ 1 trở lên");
                }
            }
            case DATE -> {
                requireNull(c.maxLength(), "maxLength", type);
                requireNull(c.min(), "min", type);
                requireNull(c.max(), "max", type);
                requireNull(c.integerOnly(), "integerOnly", type);
                requireNull(c.maxSelections(), "maxSelections", type);
            }
        }
    }

    private static void requireNull(Object value, String field, InputType type) {
        if (value != null) {
            throw invalid(field + " không áp dụng cho " + type);
        }
    }

    private static DomainException invalid(String message) {
        return new DomainException(CategoryErrorCode.ATTRIBUTE_CONSTRAINTS_INVALID, message);
    }

    // ───────────── Getters ─────────────

    public AttributeTemplateId getAttributeTemplateId() { return attributeTemplateId; }
    public boolean isRequired()                         { return required; }
    public int getDisplayOrder()                        { return displayOrder; }
    public AttributeConstraints getConstraints()        { return constraints; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CategoryAttributeAssignment other)) return false;
        return Objects.equals(attributeTemplateId, other.attributeTemplateId);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(attributeTemplateId);
    }
}
