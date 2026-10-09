package vn.t3nexus.catalog.domain.category;

import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplate;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplateId;
import vn.t3nexus.catalog.domain.attributetemplate.InputType;
import vn.t3nexus.lib.common.domain.exception.DomainException;
import vn.t3nexus.lib.common.domain.model.ValueObject;

import java.util.Objects;

/** Cách 1 template được dùng ở 1 danh mục lá (AGG-CAT-03). Equality chỉ theo templateId — 2 assignment cùng
 *  template là trùng lặp, bất kể cấu hình khác. */
public class CategoryAttributeAssignment implements ValueObject {

    private final AttributeTemplateId attributeTemplateId;
    private final boolean required;
    private final int displayOrder;
    private final AttributeConstraints constraints;
    private final Discovery discovery;

    private CategoryAttributeAssignment(AttributeTemplateId attributeTemplateId, boolean required, int displayOrder,
                                        AttributeConstraints constraints, Discovery discovery) {
        this.attributeTemplateId = attributeTemplateId;
        this.required            = required;
        this.displayOrder        = displayOrder;
        this.constraints         = constraints;
        this.discovery           = discovery;
    }

    /** Tạo mới — kiểm constraints/discovery hợp lệ với inputType của template (bảng "Quy tắc theo kiểu nhập" AGG-CAT-01). */
    public static CategoryAttributeAssignment create(AttributeTemplateId templateId, InputType inputType,
                                                      boolean required, int displayOrder,
                                                      AttributeConstraints constraints, Discovery discovery) {
        AttributeConstraints c = constraints == null ? AttributeConstraints.NONE : constraints;
        Discovery d = discovery == null ? Discovery.NONE : discovery;
        validateConstraints(inputType, c);
        validateDiscovery(inputType, d);
        validateStepAgainstInputPrecision(inputType, c, d);
        return new CategoryAttributeAssignment(templateId, required, displayOrder, c, d);
    }

    /** Đọc lại từ persistence — dữ liệu đã qua {@link #create} lúc ghi, không kiểm lại. */
    public static CategoryAttributeAssignment reconstitute(AttributeTemplateId templateId, boolean required,
                                                            int displayOrder, AttributeConstraints constraints,
                                                            Discovery discovery) {
        return new CategoryAttributeAssignment(templateId, required, displayOrder,
                constraints == null ? AttributeConstraints.NONE : constraints,
                discovery == null ? Discovery.NONE : discovery);
    }

    // ───────────── Validation — bảng "Quy tắc theo kiểu nhập" (analysis.md AGG-CAT-01) ─────────────

    private static void validateConstraints(InputType type, AttributeConstraints c) {
        switch (type) {
            case SINGLE_SELECT, BOOLEAN -> {
                if (!AttributeConstraints.NONE.equals(c)) {
                    throw new DomainException(CategoryErrorCode.INVALID_CONSTRAINTS, type + " không có constraints");
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
                if (c.maxLength() != null
                        && (c.maxLength() < 1 || c.maxLength() > AttributeTemplate.TEXT_MAX_LENGTH)) {
                    throw new DomainException(CategoryErrorCode.INVALID_CONSTRAINTS,
                            "maxLength phải trong khoảng 1.." + AttributeTemplate.TEXT_MAX_LENGTH);
                }
            }
            case NUMBER -> {
                requireNull(c.maxLength(), "maxLength", type);
                requireNull(c.inputPrecision(), "inputPrecision", type);
                requireNull(c.minDate(), "minDate", type);
                requireNull(c.maxDate(), "maxDate", type);
                requireNull(c.maxSelections(), "maxSelections", type);
                if (c.min() != null && c.max() != null && c.min().compareTo(c.max()) > 0) {
                    throw new DomainException(CategoryErrorCode.INVALID_CONSTRAINTS, "min phải nhỏ hơn hoặc bằng max");
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
                    throw new DomainException(CategoryErrorCode.INVALID_CONSTRAINTS, "maxSelections phải ≥ 1");
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
            throw new DomainException(CategoryErrorCode.INVALID_CONSTRAINTS, field + " không áp dụng cho " + type);
        }
    }

    private static void validateDiscovery(InputType type, Discovery d) {
        if (d.search() != null && type == InputType.DATE) {
            throw new DomainException(CategoryErrorCode.INVALID_DISCOVERY, "DATE không hỗ trợ search");
        }
        validateFilter(type, d.filter());
        if (d.sort() != null && type != InputType.NUMBER && type != InputType.DATE) {
            throw new DomainException(CategoryErrorCode.INVALID_DISCOVERY, "sort chỉ áp dụng cho NUMBER/DATE");
        }
    }

    private static void validateFilter(InputType type, FilterConfig f) {
        if (f == null) return;
        FilterConfig.Kind expectedKind = switch (type) {
            case SINGLE_SELECT, MULTI_SELECT -> FilterConfig.Kind.SELECT;
            case BOOLEAN -> FilterConfig.Kind.BOOLEAN;
            case NUMBER, DATE -> FilterConfig.Kind.RANGE;
            case TEXT -> null; // TEXT không hỗ trợ filter
        };
        if (expectedKind == null || f.kind() != expectedKind) {
            throw new DomainException(CategoryErrorCode.INVALID_DISCOVERY, "filter không hợp lệ với kiểu " + type);
        }
        if (f.kind() == FilterConfig.Kind.SELECT && type == InputType.SINGLE_SELECT
                && f.match() == FilterConfig.MatchMode.ALL) {
            throw new DomainException(CategoryErrorCode.INVALID_DISCOVERY, "match=ALL chỉ áp dụng cho MULTI_SELECT");
        }
        if (f.kind() == FilterConfig.Kind.RANGE) {
            validateRangeFilter(type, f);
        }
    }

    private static void validateRangeFilter(InputType type, FilterConfig f) {
        if (f.presentation() == null) {
            throw new DomainException(CategoryErrorCode.INVALID_RANGE_FILTER, "presentation bắt buộc cho RANGE");
        }
        if (f.step() != null && type != InputType.DATE) {
            throw new DomainException(CategoryErrorCode.INVALID_RANGE_FILTER, "step chỉ áp dụng cho DATE");
        }
        if (f.presentation() == FilterConfig.Presentation.SLIDER && type == InputType.DATE && f.step() == null) {
            throw new DomainException(CategoryErrorCode.INVALID_RANGE_FILTER, "SLIDER trên DATE bắt buộc step");
        }
        if (f.presentation() == FilterConfig.Presentation.BUCKETS
                && (f.buckets() == null || f.buckets().isEmpty())) {
            throw new DomainException(CategoryErrorCode.INVALID_RANGE_FILTER, "BUCKETS bắt buộc có ít nhất 1 khoảng");
        }
        if (f.buckets() != null) {
            for (FilterConfig.Bucket b : f.buckets()) {
                if (b.from() == null && b.to() == null) {
                    throw new DomainException(CategoryErrorCode.INVALID_RANGE_FILTER,
                            "Mỗi bucket cần ít nhất from hoặc to");
                }
            }
        }
        // Độ chính xác mốc tuyệt đối (from/to) so với inputPrecision: chưa kiểm — cú pháp mốc tương đối
        // (now-30d) chưa chốt cho search bậc 2 (search-service analysis.md §10).
    }

    private static void validateStepAgainstInputPrecision(InputType type, AttributeConstraints c, Discovery d) {
        if (type != InputType.DATE || d.filter() == null || d.filter().step() == null || c.inputPrecision() == null) {
            return;
        }
        if (!d.filter().step().isCoarserThanOrEqualTo(c.inputPrecision())) {
            throw new DomainException(CategoryErrorCode.INVALID_RANGE_FILTER,
                    "step phải thô hơn hoặc bằng inputPrecision (dữ liệu chỉ chính xác tới " + c.inputPrecision() + ")");
        }
    }

    // ───────────── Getters ─────────────

    public AttributeTemplateId getAttributeTemplateId() { return attributeTemplateId; }
    public boolean isRequired()                         { return required; }
    public int getDisplayOrder()                        { return displayOrder; }
    public AttributeConstraints getConstraints()        { return constraints; }
    public Discovery getDiscovery()                     { return discovery; }

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
