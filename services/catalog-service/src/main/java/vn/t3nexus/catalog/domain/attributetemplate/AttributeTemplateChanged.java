package vn.t3nexus.catalog.domain.attributetemplate;

import vn.t3nexus.lib.common.domain.model.AbstractDomainEvent;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Mẫu thuộc tính đã đổi (tạo, sửa chi tiết, ngừng, bật lại, hoặc đổi tùy chọn). Một loại event cho mọi thay đổi, mang
 * {@link Change}. {@code version} là phiên bản SAU thay đổi ({@code 0} khi tạo). {@code value} và {@code inputType} chỉ
 * dùng cho bản ghi kiểm toán (tên mẫu và kiểu khi tạo; giá trị tùy chọn khi đổi tùy chọn), không vào hộp thư.
 */
public final class AttributeTemplateChanged extends AbstractDomainEvent {

    public enum Change {
        CREATED, DETAILS_UPDATED, DEACTIVATED, ACTIVATED,
        OPTION_ADDED, OPTION_LABEL_UPDATED, OPTION_DEACTIVATED, OPTION_ACTIVATED, OPTIONS_REORDERED
    }

    private final AttributeTemplateId templateId;
    private final long version;
    private final Change change;
    private final UUID actorId;
    private final UUID optionId;
    private final String value;
    private final InputType inputType;

    public AttributeTemplateChanged(UUID eventId, Instant occurredOn, AttributeTemplateId templateId, long version,
                                    Change change, UUID actorId, UUID optionId, String value, InputType inputType) {
        super(Objects.requireNonNull(eventId, "eventId").toString(),
                Objects.requireNonNull(occurredOn, "occurredOn"),
                Objects.requireNonNull(templateId, "templateId").getValue().toString(),
                "AttributeTemplate");
        this.templateId = templateId;
        this.version = version;
        this.change = Objects.requireNonNull(change, "change");
        this.actorId = Objects.requireNonNull(actorId, "actorId");
        this.optionId = optionId;
        this.value = value;
        this.inputType = inputType;
    }

    public AttributeTemplateId templateId() { return templateId; }
    public long version()                   { return version; }
    public Change change()                  { return change; }
    public UUID actorId()                   { return actorId; }
    public UUID optionId()                  { return optionId; }
    public String value()                   { return value; }
    public InputType inputType()            { return inputType; }

    @Override
    public String getRoutingKey() {
        return "catalog.attribute-template.changed";
    }

    @Override
    public Object getPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("templateId", templateId.getValue().toString());
        payload.put("version", Long.valueOf(version));
        payload.put("change", change.name());
        payload.put("occurredAt", getOccurredOn().toString());
        return Collections.unmodifiableMap(payload);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[eventId=" + getEventId() + ", templateId=" + templateId.getValue()
                + ", change=" + change + "]";
    }
}
