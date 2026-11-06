package vn.t3nexus.catalog.domain.category;

import vn.t3nexus.lib.common.domain.model.AbstractDomainEvent;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Danh mục đã đổi (tạo, đổi tên, sắp xếp, ngừng, bật lại, thay danh sách gán, xóa). Một loại event cho mọi thay đổi,
 * mang {@link Change}. {@code version} là phiên bản SAU thay đổi ({@code 0} khi tạo). Các trường còn lại ({@code name},
 * {@code oldName}, {@code parentId}, {@code level}, {@code count}) chỉ dùng cho bản ghi kiểm toán, không vào hộp thư.
 */
public final class CategoryChanged extends AbstractDomainEvent {

    public enum Change { CREATED, RENAMED, REORDERED, ASSIGNMENTS_REPLACED, DEACTIVATED, ACTIVATED, DELETED }

    private final CategoryId categoryId;
    private final long version;
    private final Change change;
    private final UUID actorId;
    private final String name;
    private final String oldName;
    private final UUID parentId;
    private final Integer level;
    private final Integer count;

    public CategoryChanged(UUID eventId, Instant occurredOn, CategoryId categoryId, long version, Change change,
                           UUID actorId, String name, String oldName, UUID parentId, Integer level, Integer count) {
        super(Objects.requireNonNull(eventId, "eventId").toString(),
                Objects.requireNonNull(occurredOn, "occurredOn"),
                Objects.requireNonNull(categoryId, "categoryId").getValue().toString(),
                "Category");
        this.categoryId = categoryId;
        this.version = version;
        this.change = Objects.requireNonNull(change, "change");
        this.actorId = Objects.requireNonNull(actorId, "actorId");
        this.name = name;
        this.oldName = oldName;
        this.parentId = parentId;
        this.level = level;
        this.count = count;
    }

    public CategoryId categoryId() { return categoryId; }
    public long version()          { return version; }
    public Change change()         { return change; }
    public UUID actorId()          { return actorId; }

    /** Tên sau thay đổi: có khi tạo, đổi tên và xóa (xóa xong không còn chỗ tra tên). */
    public String name()           { return name; }

    /** Tên trước khi đổi; chỉ có khi {@link Change#RENAMED}. */
    public String oldName()        { return oldName; }

    /** Cha của danh mục; null ở cấp một. Có khi tạo, sắp xếp và xóa. */
    public UUID parentId()         { return parentId; }

    /** Cấp (1 đến 3); chỉ có khi tạo. */
    public Integer level()         { return level; }

    /** Số dòng gán sau khi thay; chỉ có khi {@link Change#ASSIGNMENTS_REPLACED}. */
    public Integer count()         { return count; }

    @Override
    public String getRoutingKey() {
        return "catalog.category.changed";
    }

    @Override
    public Object getPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("categoryId", categoryId.getValue().toString());
        payload.put("version", Long.valueOf(version));
        payload.put("change", change.name());
        payload.put("occurredAt", getOccurredOn().toString());
        return Collections.unmodifiableMap(payload);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[eventId=" + getEventId() + ", categoryId=" + categoryId.getValue()
                + ", change=" + change + "]";
    }
}
