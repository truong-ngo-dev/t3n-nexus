package vn.t3nexus.catalog.domain.attributetemplate;

import vn.t3nexus.lib.common.domain.model.AbstractEntity;
import vn.t3nexus.lib.common.domain.model.Entity;

import java.time.Instant;

/**
 * Giá trị chuẩn hóa của thuộc tính kiểu chọn. Thực thể bên trong {@link AttributeTemplate}; bên ngoài tham chiếu bằng
 * cặp {@code (templateId, optionId)}. {@code value} đặt lúc thêm, không đổi.
 * <br>Ngừng, bật không có guard riêng: ngừng chỉ chặn chọn MỚI (kiểm ở nơi chọn), không đụng giá trị đã lưu.
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

    static AttributeOption create(AttributeOptionId id, String value, String displayValue,
                                  int sortOrder, Instant now) {
        return new AttributeOption(id, value, displayValue, AttributeOptionStatus.ACTIVE, sortOrder, now);
    }

    public static AttributeOption reconstitute(AttributeOptionId id, String value, String displayValue,
                                               AttributeOptionStatus status, int sortOrder, Instant createdAt) {
        return new AttributeOption(id, value, displayValue, status, sortOrder, createdAt);
    }

    void deactivate()                            { this.status = AttributeOptionStatus.INACTIVE; }
    void activate()                              { this.status = AttributeOptionStatus.ACTIVE; }
    void updateDisplayValue(String displayValue) { this.displayValue = displayValue; }
    void changeSortOrder(int sortOrder)          { this.sortOrder = sortOrder; }

    public boolean isActive()                { return status == AttributeOptionStatus.ACTIVE; }
    public String getValue()                 { return value; }
    public String getDisplayValue()          { return displayValue; }
    public AttributeOptionStatus getStatus() { return status; }
    public int getSortOrder()                { return sortOrder; }
    public Instant getCreatedAt()            { return createdAt; }
}
