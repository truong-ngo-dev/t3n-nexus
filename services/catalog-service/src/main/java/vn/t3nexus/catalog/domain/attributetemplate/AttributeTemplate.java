package vn.t3nexus.catalog.domain.attributetemplate;

import vn.t3nexus.catalog.domain.CatalogErrorCode;
import vn.t3nexus.catalog.domain.CatalogFieldException;
import vn.t3nexus.catalog.domain.ChangeContext;
import vn.t3nexus.catalog.domain.NameKey;
import vn.t3nexus.lib.common.domain.exception.DomainException;
import vn.t3nexus.lib.common.domain.model.AbstractAggregateRoot;
import vn.t3nexus.lib.common.domain.model.AggregateRoot;

import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Một đặc tính sản phẩm dùng chung toàn sàn kèm tập giá trị chuẩn (tùy chọn). Chỉ mang Ý NGHĨA (kiểu, đơn vị, giá trị
 * hợp lệ). Kiểu nhập và đơn vị đặt lúc tạo, không đổi (INV-CAT-12); mẫu, tùy chọn không bị xóa, chỉ ngừng dùng.
 * <br>Mọi hành vi ghi nhận {@link ChangeContext} và phát {@link AttributeTemplateChanged}; phiên bản trong event là
 * phiên bản SAU thay đổi ({@code version() + 1}, hoặc {@code 0} khi tạo mới).
 */
public class AttributeTemplate extends AbstractAggregateRoot<AttributeTemplateId>
        implements AggregateRoot<AttributeTemplateId> {

    public static final int NAME_MAX_LENGTH = 100;
    public static final int DISPLAY_NAME_MAX_LENGTH = 100;
    public static final int HINT_MAX_LENGTH = 200;
    public static final int UNIT_MAX_LENGTH = 20;
    public static final int OPTION_VALUE_MAX_LENGTH = 100;

    // Guard mặc định theo kiểu: tham số hệ thống, không phải cấu hình của quản trị.
    public static final int TEXT_MAX_LENGTH = 255;
    public static final int NUMBER_MAX_SCALE = 4;
    // Mã kỹ thuật: bắt buộc đúng dạng, sai thì từ chối chứ không tự sửa hộ. Tên mẫu chỉ gạch dưới; giá trị tùy chọn thêm gạch ngang.
    private static final Pattern NAME_PATTERN = Pattern.compile("[a-z0-9]+(_[a-z0-9]+)*");
    private static final Pattern VALUE_PATTERN = Pattern.compile("[a-z0-9]+([_-][a-z0-9]+)*");
    private static final Pattern DATE_PATTERN = Pattern.compile("\\d{4}(-\\d{2}(-\\d{2})?)?");

    private final String name;
    private String displayName;
    private String hint;
    private final InputType inputType;
    private final String unit;
    private AttributeTemplateStatus status;
    private final List<AttributeOption> options;
    private final Long version;
    private final Instant createdAt;
    private Instant updatedAt;

    private AttributeTemplate(AttributeTemplateId id, String name, String displayName, String hint,
                              InputType inputType, String unit, AttributeTemplateStatus status,
                              List<AttributeOption> options, Long version, Instant createdAt, Instant updatedAt) {
        setId(id);
        this.name        = name;
        this.displayName = displayName;
        this.hint        = hint;
        this.inputType   = inputType;
        this.unit        = unit;
        this.status      = status;
        this.options     = new ArrayList<>(options);
        this.version     = version;
        this.createdAt   = createdAt;
        this.updatedAt   = updatedAt;
    }

    // ───────────── Factory ─────────────

    /** Kiểm dạng tên mẫu, ném 400 nếu sai. Use case dùng để kiểm sớm tên đã có trước khi tạo. */
    public static String validName(String name) {
        return code("name", name, NAME_MAX_LENGTH, NAME_PATTERN);
    }

    public static AttributeTemplate create(AttributeTemplateId id, String name, String displayName, String hint,
                                           InputType inputType, String unit, ChangeContext ctx) {
        String code = validName(name);
        String shownName = normalizedName("displayName", displayName, DISPLAY_NAME_MAX_LENGTH).display();
        if (inputType == null) {
            throw new CatalogFieldException("inputType");
        }
        String cleanUnit = optionalText("unit", unit, UNIT_MAX_LENGTH);
        // unit là Ý NGHĨA của con số: chỉ NUMBER có; bất biến (đổi đơn vị là đổi nghĩa mọi số đã lưu).
        if (cleanUnit != null && inputType != InputType.NUMBER) {
            throw new CatalogFieldException("unit");
        }
        AttributeTemplate template = new AttributeTemplate(id, code, shownName,
                optionalText("hint", hint, HINT_MAX_LENGTH), inputType, cleanUnit, AttributeTemplateStatus.ACTIVE,
                List.of(), null, ctx.now(), ctx.now());
        template.emit(ctx, AttributeTemplateChanged.Change.CREATED, null, template.name);
        return template;
    }

    public static AttributeTemplate reconstitute(AttributeTemplateId id, String name,
                                                 String displayName, String hint, InputType inputType, String unit,
                                                 AttributeTemplateStatus status, List<AttributeOption> options,
                                                 Long version, Instant createdAt, Instant updatedAt) {
        return new AttributeTemplate(id, name, displayName, hint, inputType, unit, status, options, version,
                createdAt, updatedAt);
    }

    // ───────────── Hành vi ─────────────

    public void updateDetails(String displayName, String hint, ChangeContext ctx) {
        this.displayName = normalizedName("displayName", displayName, DISPLAY_NAME_MAX_LENGTH).display();
        this.hint        = optionalText("hint", hint, HINT_MAX_LENGTH);
        touch(ctx, AttributeTemplateChanged.Change.DETAILS_UPDATED, null, null);
    }

    public void deactivate(ChangeContext ctx) {
        if (status == AttributeTemplateStatus.INACTIVE) {
            throw new DomainException(CatalogErrorCode.CATALOG_INVALID_STATE);
        }
        this.status = AttributeTemplateStatus.INACTIVE;
        touch(ctx, AttributeTemplateChanged.Change.DEACTIVATED, null, null);
    }

    public void activate(ChangeContext ctx) {
        if (status == AttributeTemplateStatus.ACTIVE) {
            throw new DomainException(CatalogErrorCode.CATALOG_INVALID_STATE);
        }
        this.status = AttributeTemplateStatus.ACTIVE;
        touch(ctx, AttributeTemplateChanged.Change.ACTIVATED, null, null);
    }

    /** Tùy chọn mới luôn xếp cuối. Chỉ mẫu kiểu chọn, đang dùng; {@code value} đúng dạng mã và duy nhất trong mẫu. */
    public void addOption(AttributeOptionId optionId, String value, String displayValue, ChangeContext ctx) {
        String code = code("value", value, OPTION_VALUE_MAX_LENGTH, VALUE_PATTERN);
        String shownValue = normalizedName("displayValue", displayValue, OPTION_VALUE_MAX_LENGTH).display();
        if (!inputType.isSelect()) {
            throw new DomainException(AttributeTemplateErrorCode.ATTRIBUTE_INPUT_TYPE_NOT_SELECT);
        }
        if (status == AttributeTemplateStatus.INACTIVE) {
            throw new DomainException(AttributeTemplateErrorCode.ATTRIBUTE_TEMPLATE_INACTIVE);
        }
        if (options.stream().anyMatch(o -> o.getValue().equals(code))) {
            throw new DomainException(AttributeTemplateErrorCode.ATTRIBUTE_OPTION_VALUE_TAKEN);
        }
        int nextOrder = options.stream().mapToInt(AttributeOption::getSortOrder).max().orElse(-1) + 1;
        options.add(AttributeOption.create(optionId, code, shownValue, nextOrder, ctx.now()));
        touch(ctx, AttributeTemplateChanged.Change.OPTION_ADDED, optionId, code);
    }

    /** Đổi cách gọi, áp ngay mọi nơi đang dùng; sửa được bất kể trạng thái của mẫu hay của tùy chọn. */
    public void updateOptionDisplayValue(AttributeOptionId optionId, String displayValue, ChangeContext ctx) {
        String shownValue = normalizedName("displayValue", displayValue, OPTION_VALUE_MAX_LENGTH).display();
        AttributeOption option = findOption(optionId);
        option.updateDisplayValue(shownValue);
        touch(ctx, AttributeTemplateChanged.Change.OPTION_LABEL_UPDATED, optionId, option.getValue());
    }

    /** Ngừng tùy chọn không bị kiểm: ngừng tùy chọn cuối cùng được phép, mẫu thành không dùng được. */
    public void deactivateOption(AttributeOptionId optionId, ChangeContext ctx) {
        AttributeOption option = findOption(optionId);
        if (!option.isActive()) {
            throw new DomainException(CatalogErrorCode.CATALOG_INVALID_STATE);
        }
        option.deactivate();
        touch(ctx, AttributeTemplateChanged.Change.OPTION_DEACTIVATED, optionId, option.getValue());
    }

    public void activateOption(AttributeOptionId optionId, ChangeContext ctx) {
        AttributeOption option = findOption(optionId);
        if (option.isActive()) {
            throw new DomainException(CatalogErrorCode.CATALOG_INVALID_STATE);
        }
        option.activate();
        touch(ctx, AttributeTemplateChanged.Change.OPTION_ACTIVATED, optionId, option.getValue());
    }

    /** Sắp lại thứ tự chuẩn: danh sách phải chứa MỌI tùy chọn của mẫu, mỗi tùy chọn đúng một lần. */
    public void reorderOptions(List<AttributeOptionId> orderedIds, ChangeContext ctx) {
        Set<AttributeOptionId> distinct = new HashSet<>(orderedIds);
        Set<AttributeOptionId> current  = new HashSet<>(options.stream().map(AttributeOption::getId).toList());
        if (distinct.size() != orderedIds.size() || !distinct.equals(current)) {
            throw new DomainException(AttributeTemplateErrorCode.ATTRIBUTE_INVALID_OPTION_ORDER);
        }
        for (int i = 0; i < orderedIds.size(); i++) {
            findOption(orderedIds.get(i)).changeSortOrder(i);
        }
        touch(ctx, AttributeTemplateChanged.Change.OPTIONS_REORDERED, null, null);
    }

    /**
     * Còn được chọn MỚI không: ACTIVE, và với kiểu chọn phải còn ít nhất một tùy chọn ACTIVE. Như nhau ở mọi danh mục.
     * Không dùng được thì giao diện người bán không hiện, không khai giá trị mới; dữ liệu cũ giữ nguyên.
     */
    public boolean isUsable() {
        if (status != AttributeTemplateStatus.ACTIVE) return false;
        return !inputType.isSelect() || options.stream().anyMatch(AttributeOption::isActive);
    }

    /**
     * Kiểm định dạng mặc định theo kiểu cho kiểu KHÔNG phải chọn (kiểu chọn kiểm tùy chọn ở nơi chọn). Chặn dữ liệu
     * rác, không phải quy tắc nghiệp vụ: ràng buộc theo ngành hàng (min, max, độ dài...) thuộc dòng gán của danh mục.
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

    /** Phiên bản SAU thay đổi sắp lưu: {@code 0} khi chưa lưu, ngược lại {@code version() + 1}. */
    public long nextVersion() {
        return version == null ? 0L : version + 1;
    }

    // ───────────── Nội bộ ─────────────

    private void touch(ChangeContext ctx, AttributeTemplateChanged.Change change, AttributeOptionId optionId,
                       String value) {
        this.updatedAt = ctx.now();
        emit(ctx, change, optionId, value);
    }

    private void emit(ChangeContext ctx, AttributeTemplateChanged.Change change, AttributeOptionId optionId,
                      String value) {
        UUID optionUuid = optionId == null ? null : optionId.getValue();
        InputType type = change == AttributeTemplateChanged.Change.CREATED ? inputType : null;
        addDomainEvent(new AttributeTemplateChanged(ctx.eventId(), ctx.now(), getId(), nextVersion(), change,
                ctx.actor().accountId(), optionUuid, value, type));
    }

    private AttributeOption findOption(AttributeOptionId optionId) {
        return options.stream()
                .filter(o -> o.getId().equals(optionId))
                .findFirst()
                .orElseThrow(() -> new DomainException(AttributeTemplateErrorCode.ATTRIBUTE_OPTION_NOT_FOUND));
    }

    // Mã kỹ thuật: không cắt, không đổi chữ; khác dạng (kể cả thừa dấu cách) thì từ chối.
    private static String code(String field, String raw, int maxLength, Pattern pattern) {
        if (raw == null || raw.length() > maxLength || !pattern.matcher(raw).matches()) {
            throw new CatalogFieldException(field);
        }
        return raw;
    }

    private static NameKey normalizedName(String field, String raw, int maxLength) {
        if (raw == null || raw.isBlank()) {
            throw new CatalogFieldException(field);
        }
        NameKey name = NameKey.of(raw);
        if (name.display().length() > maxLength) {
            throw new CatalogFieldException(field);
        }
        return name;
    }

    private static String optionalText(String field, String raw, int maxLength) {
        if (raw == null || raw.isBlank()) return null;
        String text = raw.strip();
        if (text.length() > maxLength) {
            throw new CatalogFieldException(field);
        }
        return text;
    }

    private static boolean isValidNumber(String raw) {
        try {
            BigDecimal number = new BigDecimal(raw.trim());
            return number.stripTrailingZeros().scale() <= NUMBER_MAX_SCALE;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    // ISO rút gọn, chuỗi tự mang độ chính xác: yyyy | yyyy-MM | yyyy-MM-dd; phải là ngày có thật; năm 1000–9999.
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

    // ───────────── Getters ─────────────

    public String getName()                    { return name; }
    public String getDisplayName()             { return displayName; }
    public String getHint()                    { return hint; }
    public InputType getInputType()            { return inputType; }
    public String getUnit()                    { return unit; }
    public AttributeTemplateStatus getStatus() { return status; }
    public List<AttributeOption> getOptions()  { return Collections.unmodifiableList(options); }
    public Long version()                      { return version; }
    public Instant getCreatedAt()              { return createdAt; }
    public Instant getUpdatedAt()              { return updatedAt; }
}
