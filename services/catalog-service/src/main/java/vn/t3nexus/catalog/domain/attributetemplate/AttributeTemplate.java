package vn.t3nexus.catalog.domain.attributetemplate;

import vn.t3nexus.lib.common.domain.exception.DomainException;
import vn.t3nexus.lib.common.domain.model.AbstractAggregateRoot;
import vn.t3nexus.lib.common.domain.model.AggregateRoot;

import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Một đặc tính sản phẩm dùng chung toàn sàn + tập giá trị chuẩn (option). Chỉ mang Ý NGHĨA (kiểu, đơn vị, giá trị hợp
 * lệ);
 */
public class AttributeTemplate extends AbstractAggregateRoot<AttributeTemplateId>
        implements AggregateRoot<AttributeTemplateId> {

    public static final int HINT_MAX_LENGTH = 200;

    // Guard mặc định theo kiểu — tham số hệ thống, không phải cấu hình của Admin.
    public static final int TEXT_MAX_LENGTH = 255;
    public static final int NUMBER_MAX_SCALE = 4;
    private static final Pattern DATE_PATTERN = Pattern.compile("\\d{4}(-\\d{2}(-\\d{2})?)?");

    private final String name;
    private String displayName;
    private String hint;
    private final InputType inputType;
    private final String unit;
    private AttributeTemplateStatus status;
    private final List<AttributeOption> options;
    private final Instant createdAt;
    private Instant updatedAt;

    private AttributeTemplate(AttributeTemplateId id, String name, String displayName, String hint,
                              InputType inputType, String unit, AttributeTemplateStatus status,
                              List<AttributeOption> options, Instant createdAt, Instant updatedAt) {
        setId(id);
        this.name        = name;
        this.displayName = displayName;
        this.hint        = hint;
        this.inputType   = inputType;
        this.unit        = unit;
        this.status      = status;
        this.options     = new ArrayList<>(options);
        this.createdAt   = createdAt;
        this.updatedAt   = updatedAt;
    }

    // ───────────── Factory Methods ─────────────

    public static AttributeTemplate create(AttributeTemplateId id, String name, String displayName, String hint,
                                           InputType inputType, String unit) {
        // unit là Ý NGHĨA của con số — chỉ NUMBER có; bất biến (đổi đơn vị = đổi nghĩa mọi số đã lưu).
        if (unit != null && inputType != InputType.NUMBER) {
            throw new DomainException(AttributeTemplateErrorCode.UNIT_ONLY_FOR_NUMBER);
        }
        guardHint(hint);
        Instant now = Instant.now();
        return new AttributeTemplate(id, name, displayName, hint, inputType, unit,
                AttributeTemplateStatus.ACTIVE, List.of(), now, now);
    }

    public static AttributeTemplate reconstitute(AttributeTemplateId id, String name, String displayName, String hint,
                                                 InputType inputType, String unit, AttributeTemplateStatus status,
                                                 List<AttributeOption> options,
                                                 Instant createdAt, Instant updatedAt) {
        return new AttributeTemplate(id, name, displayName, hint, inputType, unit, status, options,
                createdAt, updatedAt);
    }

    // ───────────── Behaviour ─────────────

    public void updateDetails(String displayName, String hint) {
        guardHint(hint);
        this.displayName = displayName;
        this.hint        = hint;
        this.updatedAt   = Instant.now();
    }

    public void deactivate() {
        this.status    = AttributeTemplateStatus.INACTIVE;
        this.updatedAt = Instant.now();
    }

    public void activate() {
        this.status    = AttributeTemplateStatus.ACTIVE;
        this.updatedAt = Instant.now();
    }

    /** Option mới luôn xếp cuối theo thứ tự chuẩn. {@code value} duy nhất trong template (không phân biệt hoa/thường). */
    public void addOption(AttributeOptionId optionId, String value, String displayValue) {
        guardActive();
        if (!inputType.isSelect()) {
            throw new DomainException(AttributeTemplateErrorCode.INPUT_TYPE_NOT_SELECT);
        }
        boolean duplicated = options.stream().anyMatch(o -> o.getValue().equalsIgnoreCase(value));
        if (duplicated) {
            throw new DomainException(AttributeTemplateErrorCode.OPTION_VALUE_EXISTS);
        }
        int nextOrder = options.stream().mapToInt(AttributeOption::getSortOrder).max().orElse(-1) + 1;
        options.add(AttributeOption.create(optionId, value, displayValue, nextOrder));
        this.updatedAt = Instant.now();
    }

    public void deactivateOption(AttributeOptionId optionId) {
        findOption(optionId).deactivate();
        this.updatedAt = Instant.now();
    }

    public void activateOption(AttributeOptionId optionId) {
        findOption(optionId).activate();
        this.updatedAt = Instant.now();
    }

    /** Đổi cách gọi — áp ngay mọi nơi đang dùng (tham chiếu bằng ID), kể cả option đã tắt HOẶC template đã tắt.
     *  Đổi NGHĨA thì tạo option mới + tắt option cũ. "Sửa" không thuộc loại hành vi bị trạng thái chi phối
     *  (khác "Thêm mới"/"Chọn dùng") — cùng nguyên tắc với {@link #updateDetails}, {@code UpdateCategory},
     *  {@code UpdateBrand} (đều sửa được bất kể trạng thái, xem analysis.md AGG-CAT-01 mục 3). */
    public void updateOptionDisplayValue(AttributeOptionId optionId, String displayValue) {
        findOption(optionId).updateDisplayValue(displayValue);
        this.updatedAt = Instant.now();
    }

    /** Sắp lại thứ tự chuẩn: danh sách phải chứa MỌI option của template, mỗi option đúng 1 lần. */
    public void reorderOptions(List<AttributeOptionId> orderedIds) {
        Set<AttributeOptionId> distinct = new HashSet<>(orderedIds);
        Set<AttributeOptionId> current  = new HashSet<>(options.stream().map(AttributeOption::getId).toList());
        if (distinct.size() != orderedIds.size() || !distinct.equals(current)) {
            throw new DomainException(AttributeTemplateErrorCode.INVALID_OPTION_ORDER);
        }
        for (int i = 0; i < orderedIds.size(); i++) {
            findOption(orderedIds.get(i)).changeSortOrder(i);
        }
        this.updatedAt = Instant.now();
    }

    /**
     * Còn được chọn MỚI không: ACTIVE, và với SELECT phải còn ít nhất 1 option ACTIVE. Như nhau ở mọi danh mục.
     * Không dùng được → form seller không hiện, không khai giá trị mới; dữ liệu cũ giữ nguyên.
     */
    public boolean isUsable() {
        if (status != AttributeTemplateStatus.ACTIVE) return false;
        return !inputType.isSelect() || options.stream().anyMatch(AttributeOption::isActive);
    }

    /**
     * Guard mặc định theo kiểu cho kiểu KHÔNG phải SELECT (SELECT kiểm option ở nơi chọn). Chặn dữ liệu rác, không phải
     * quy tắc nghiệp vụ — ràng buộc theo ngành hàng (min/max, maxLength…) thuộc assignment của danh mục.
     */
    public boolean isValidRawValue(String raw) {
        if (raw == null) return false;
        return switch (inputType) {
            case TEXT -> {
                String trimmed = raw.trim();
                yield !trimmed.isEmpty() && trimmed.length() <= TEXT_MAX_LENGTH;
            }
            case NUMBER -> isValidNumber(raw);
            case BOOLEAN -> "true".equalsIgnoreCase(raw) || "false".equalsIgnoreCase(raw);
            case DATE -> isValidDate(raw);
            case SINGLE_SELECT, MULTI_SELECT -> true;
        };
    }

    public List<AttributeOption> getOptionsInOrder() {
        return options.stream().sorted(Comparator.comparingInt(AttributeOption::getSortOrder)).toList();
    }

    private static boolean isValidNumber(String raw) {
        try {
            BigDecimal number = new BigDecimal(raw.trim());
            return number.stripTrailingZeros().scale() <= NUMBER_MAX_SCALE;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    // ISO rút gọn — chuỗi tự mang độ chính xác: yyyy | yyyy-MM | yyyy-MM-dd; phải là ngày có thật; năm 1000–9999.
    private static boolean isValidDate(String raw) {
        if (!DATE_PATTERN.matcher(raw).matches()) return false;
        try {
            int year = Integer.parseInt(raw.substring(0, 4));
            if (year < 1000) return false;
            switch (raw.length()) {
                case 7  -> YearMonth.parse(raw);
                case 10 -> LocalDate.parse(raw);
                default -> { /* chỉ năm */ }
            }
            return true;
        } catch (DateTimeException e) {
            return false;
        }
    }

    private static void guardHint(String hint) {
        if (hint != null && hint.length() > HINT_MAX_LENGTH) {
            throw new DomainException(AttributeTemplateErrorCode.HINT_TOO_LONG);
        }
    }

    // Template đã tắt thì không thêm option mới (hành vi "Thêm mới") — sửa nhãn/tắt-bật option vẫn được
    // (hành vi "Sửa"/chuyển trạng thái, không bị status chi phối, xem analysis.md AGG-CAT-01 mục 3).
    private void guardActive() {
        if (status == AttributeTemplateStatus.INACTIVE) {
            throw new DomainException(AttributeTemplateErrorCode.TEMPLATE_INACTIVE);
        }
    }

    private AttributeOption findOption(AttributeOptionId optionId) {
        return options.stream()
                .filter(o -> o.getId().equals(optionId))
                .findFirst()
                .orElseThrow(() -> new DomainException(AttributeTemplateErrorCode.OPTION_NOT_FOUND));
    }

    // ───────────── Getters ─────────────

    public String getName()                    { return name; }
    public String getDisplayName()             { return displayName; }
    public String getHint()                    { return hint; }
    public InputType getInputType()            { return inputType; }
    public String getUnit()                    { return unit; }
    public AttributeTemplateStatus getStatus() { return status; }
    public List<AttributeOption> getOptions()  { return Collections.unmodifiableList(options); }
    public Instant getCreatedAt()              { return createdAt; }
    public Instant getUpdatedAt()              { return updatedAt; }
}
