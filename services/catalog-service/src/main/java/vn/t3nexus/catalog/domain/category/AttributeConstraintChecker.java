package vn.t3nexus.catalog.domain.category;

import vn.t3nexus.catalog.domain.attributetemplate.InputType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Kiểm 1 giá trị seller khai theo ràng buộc riêng của danh mục ({@link AttributeConstraints}) — chồng lên guard mặc
 * định của template (giá trị đã qua {@code AttributeTemplate.isValidRawValue} trước khi tới đây). Chỉ áp cho giá trị
 * MỚI/ĐỔI: giá trị giữ nguyên được miễn kể cả khi Admin siết ràng buộc sau đó (INV-CAT-92).
 */
public final class AttributeConstraintChecker {

    // Mốc ngày tương đối: now, now-30d, now+1y, now-6m.
    private static final Pattern RELATIVE_DATE = Pattern.compile("now(?:([+-])(\\d+)([dmy]))?");

    private AttributeConstraintChecker() {}

    /** Giá trị đơn (TEXT/NUMBER/DATE). SELECT/BOOLEAN không có ràng buộc theo giá trị. */
    public static boolean satisfies(AttributeConstraints c, InputType type, String value, LocalDate today) {
        return switch (type) {
            case TEXT -> c.maxLength() == null || value.trim().length() <= c.maxLength();
            case NUMBER -> satisfiesNumber(c, new BigDecimal(value.trim()));
            case DATE -> satisfiesDate(c, value, today);
            case SINGLE_SELECT, MULTI_SELECT, BOOLEAN -> true;
        };
    }

    /** Số giá trị tối đa của thuộc tính chọn nhiều. */
    public static boolean satisfiesSelectionCount(AttributeConstraints c, int count) {
        return c.maxSelections() == null || count <= c.maxSelections();
    }

    private static boolean satisfiesNumber(AttributeConstraints c, BigDecimal number) {
        if (c.min() != null && number.compareTo(c.min()) < 0) return false;
        if (c.max() != null && number.compareTo(c.max()) > 0) return false;
        return !Boolean.TRUE.equals(c.integerOnly()) || number.stripTrailingZeros().scale() <= 0;
    }

    // Giá trị tự mang độ chính xác (yyyy | yyyy-MM | yyyy-MM-dd). So mốc theo ngày ĐẦU của khoảng giá trị biểu diễn.
    private static boolean satisfiesDate(AttributeConstraints c, String value, LocalDate today) {
        if (c.inputPrecision() != null && precisionOf(value) != c.inputPrecision()) return false;
        LocalDate date = startOf(value);
        LocalDate min = c.minDate() == null ? null : resolveBound(c.minDate(), today);
        LocalDate max = c.maxDate() == null ? null : resolveBound(c.maxDate(), today);
        if (min != null && date.isBefore(min)) return false;
        return max == null || !date.isAfter(max);
    }

    private static DatePrecision precisionOf(String value) {
        return switch (value.length()) {
            case 4 -> DatePrecision.YEAR;
            case 7 -> DatePrecision.MONTH;
            default -> DatePrecision.DAY;
        };
    }

    private static LocalDate startOf(String value) {
        return switch (value.length()) {
            case 4 -> LocalDate.of(Integer.parseInt(value), 1, 1);
            case 7 -> LocalDate.parse(value + "-01");
            default -> LocalDate.parse(value);
        };
    }

    private static LocalDate resolveBound(String bound, LocalDate today) {
        Matcher m = RELATIVE_DATE.matcher(bound);
        if (!m.matches()) return startOf(bound);
        if (m.group(1) == null) return today;
        long amount = Long.parseLong(m.group(2)) * ("-".equals(m.group(1)) ? -1 : 1);
        return switch (m.group(3)) {
            case "d" -> today.plusDays(amount);
            case "m" -> today.plusMonths(amount);
            default -> today.plusYears(amount);
        };
    }
}
