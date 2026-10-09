package vn.t3nexus.catalog.domain.category;

import java.math.BigDecimal;

/**
 * Ràng buộc nghiệp vụ theo ngành hàng, chồng lên guard mặc định của template (AGG-CAT-01). Nhánh dùng theo
 * {@code inputType} — xem bảng "Quy tắc theo kiểu nhập":
 * <pre>
 *   TEXT          maxLength
 *   NUMBER        min, max, integerOnly
 *   MULTI_SELECT  maxSelections
 *   DATE          inputPrecision, minDate, maxDate (tuyệt đối hoặc tương đối, VD "now-30d")
 *   SINGLE_SELECT, BOOLEAN   không có constraints
 * </pre>
 * Lưu jsonb ở persistence — mọi field đều có thể null (không set = không ràng buộc thêm ngoài guard mặc định).
 */
public record AttributeConstraints(
        Integer maxLength,
        BigDecimal min,
        BigDecimal max,
        Boolean integerOnly,
        DatePrecision inputPrecision,
        String minDate,
        String maxDate,
        Integer maxSelections
) {
    public static final AttributeConstraints NONE =
            new AttributeConstraints(null, null, null, null, null, null, null, null);
}
