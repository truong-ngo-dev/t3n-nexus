package vn.t3nexus.catalog.domain.brand;

import vn.t3nexus.lib.common.domain.model.AbstractDomainEvent;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Thương hiệu đã đổi (tạo, đổi tên, ngừng, bật lại). Một loại event cho mọi thay đổi, mang {@link Change}.
 * {@code version} là phiên bản SAU thay đổi ({@code 0} khi tạo). {@code name}, {@code oldName} chỉ dùng cho bản ghi
 * kiểm toán. Ở C1a event không ra hộp thư (không có bên nhận), chỉ ghi kiểm toán.
 */
public final class BrandChanged extends AbstractDomainEvent {

    public enum Change { CREATED, RENAMED, DEACTIVATED, ACTIVATED }

    private final BrandId brandId;
    private final long version;
    private final Change change;
    private final UUID actorId;
    private final String name;
    private final String oldName;

    public BrandChanged(UUID eventId, Instant occurredOn, BrandId brandId, long version, Change change, UUID actorId,
                        String name, String oldName) {
        super(Objects.requireNonNull(eventId, "eventId").toString(),
                Objects.requireNonNull(occurredOn, "occurredOn"),
                Objects.requireNonNull(brandId, "brandId").getValue().toString(),
                "Brand");
        this.brandId = brandId;
        this.version = version;
        this.change = Objects.requireNonNull(change, "change");
        this.actorId = Objects.requireNonNull(actorId, "actorId");
        this.name = name;
        this.oldName = oldName;
    }

    public BrandId brandId() { return brandId; }
    public long version()    { return version; }
    public Change change()   { return change; }
    public UUID actorId()    { return actorId; }

    /** Tên sau thay đổi: có khi tạo và khi đổi tên; null với ngừng và bật lại. */
    public String name()     { return name; }

    /** Tên trước khi đổi; chỉ có khi {@link Change#RENAMED}. */
    public String oldName()  { return oldName; }

    @Override
    public String getRoutingKey() {
        return "catalog.brand.changed";
    }

    @Override
    public Object getPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("brandId", brandId.getValue().toString());
        payload.put("version", Long.valueOf(version));
        payload.put("change", change.name());
        payload.put("occurredAt", getOccurredOn().toString());
        return Collections.unmodifiableMap(payload);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[eventId=" + getEventId() + ", brandId=" + brandId.getValue()
                + ", change=" + change + "]";
    }
}
