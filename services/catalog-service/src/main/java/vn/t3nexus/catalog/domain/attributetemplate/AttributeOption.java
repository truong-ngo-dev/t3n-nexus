package vn.t3nexus.catalog.domain.attributetemplate;

import vn.t3nexus.lib.common.domain.model.AbstractEntity;
import vn.t3nexus.lib.common.domain.model.Entity;

import java.time.Instant;

/**
 * Giá trị chuẩn hoá của thuộc tính kiểu SELECT. Entity bên trong {@link AttributeTemplate} — bên ngoài luôn tham
 * chiếu bằng cặp {@code (templateId, optionId)}.
 * <br>Tắt/bật không có guard: tắt chỉ chặn lựa chọn MỚI (kiểm ở nơi chọn), không đụng Product/Variant đang dùng —
 * nên không cần biết option đang được dùng ở đâu (đã bỏ {@code usageCount}).
 */
public class AttributeOption extends AbstractEntity<AttributeOptionId> implements Entity<AttributeOptionId> {

    private final String value;
    private String displayValue;
    private AttributeOptionStatus status;
    private int sortOrder;
    private final Instant createdAt;

    private AttributeOption(AttributeOptionId id, String value, String displayValue,
                            AttributeOptionStatus status, int sortOrder, Instant createdAt) {
        setId(id);
        this.value        = value;
        this.displayValue = displayValue;
        this.status       = status;
        this.sortOrder    = sortOrder;
        this.createdAt    = createdAt;
    }

    // ───────────── Factory Methods ─────────────

    static AttributeOption create(AttributeOptionId id, String value, String displayValue, int sortOrder) {
        return new AttributeOption(id, value, displayValue, AttributeOptionStatus.ACTIVE, sortOrder, Instant.now());
    }

    public static AttributeOption reconstitute(AttributeOptionId id, String value, String displayValue,
                                               AttributeOptionStatus status, int sortOrder, Instant createdAt) {
        return new AttributeOption(id, value, displayValue, status, sortOrder, createdAt);
    }

    // ───────────── Behaviour ─────────────

    void deactivate() {
        this.status = AttributeOptionStatus.INACTIVE;
    }

    // Ưu tiên bật lại option cũ thay vì tạo option mới cùng value — tránh 2 ID cho 1 khái niệm.
    void activate() {
        this.status = AttributeOptionStatus.ACTIVE;
    }

    void updateDisplayValue(String displayValue) {
        this.displayValue = displayValue;
    }

    void changeSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    // ───────────── Getters ─────────────

    public boolean isActive()                { return status == AttributeOptionStatus.ACTIVE; }
    public String getValue()                 { return value; }
    public String getDisplayValue()          { return displayValue; }
    public AttributeOptionStatus getStatus() { return status; }
    public int getSortOrder()                { return sortOrder; }
    public Instant getCreatedAt()            { return createdAt; }
}
