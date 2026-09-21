package vn.t3nexus.catalog.domain.attributetemplate;

import vn.t3nexus.lib.common.domain.exception.DomainException;
import vn.t3nexus.lib.common.domain.model.AbstractEntity;
import vn.t3nexus.lib.common.domain.model.Entity;

import java.time.Instant;

public class AttributeOption extends AbstractEntity<AttributeOptionId> implements Entity<AttributeOptionId> {

    private final String value;
    private String displayValue;
    private AttributeOptionStatus status;
    private final int usageCount;
    private final Instant createdAt;

    private AttributeOption(AttributeOptionId id, String value, String displayValue,
                            AttributeOptionStatus status, int usageCount, Instant createdAt) {
        setId(id);
        this.value        = value;
        this.displayValue = displayValue;
        this.status       = status;
        this.usageCount   = usageCount;
        this.createdAt    = createdAt;
    }

    // ───────────── Factory Methods ─────────────

    public static AttributeOption create(AttributeOptionId id, String value, String displayValue) {
        return new AttributeOption(id, value, displayValue, AttributeOptionStatus.ACTIVE, 0, Instant.now());
    }

    public static AttributeOption reconstitute(AttributeOptionId id, String value, String displayValue,
                                               AttributeOptionStatus status, int usageCount, Instant createdAt) {
        return new AttributeOption(id, value, displayValue, status, usageCount, createdAt);
    }

    // ───────────── Behaviour ─────────────

    // usageCount tăng qua AttributeTemplateRepository.incrementOptionUsage (bulk update riêng, không đi
    // qua aggregate này — xem javadoc port đó) mỗi khi AddVariant dùng option này trong combination.
    // Đây là lý do duy nhất để chặn deactivate: Variant giữ tham chiếu thật (FK) tới option, khác Product
    // (chỉ copy raw value, không cần guard này).
    void deactivate() {
        if (usageCount > 0) {
            throw new DomainException(AttributeTemplateErrorCode.OPTION_IN_USE);
        }
        this.status = AttributeOptionStatus.INACTIVE;
    }

    // Đối xứng deactivate() — không cần guard. Ưu tiên bật lại option cũ thay vì tạo option mới cùng
    // value, tránh phân mảnh identity (2 AttributeOptionId khác nhau cho cùng 1 khái niệm nghiệp vụ).
    void activate() {
        this.status = AttributeOptionStatus.ACTIVE;
    }

    void updateDisplayValue(String displayValue) {
        this.displayValue = displayValue;
    }

    // ───────────── Getters ─────────────

    public String getValue()                 { return value; }
    public String getDisplayValue()          { return displayValue; }
    public AttributeOptionStatus getStatus() { return status; }
    public int getUsageCount()               { return usageCount; }
    public Instant getCreatedAt()            { return createdAt; }
}
