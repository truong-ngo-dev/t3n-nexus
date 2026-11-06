package vn.t3nexus.lib.audit;

import vn.t3nexus.lib.common.domain.model.DomainEvent;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Sự kiện miền biểu thị một hành động kiểm toán đã được ghi nhận.
 * <br>Tên lớp được dùng trực tiếp làm eventType khi lưu vào bảng hộp thư.
 */
public class AuditActionRecorded implements DomainEvent {

    private final String eventId;
    private final Instant occurredOn;
    private final String sourceService;
    private final AuditActor actor;
    private final String action;
    private final AuditTarget target;
    private final Map<String, Object> details;

    public AuditActionRecorded(
            String eventId,
            Instant occurredOn,
            String sourceService,
            AuditActor actor,
            String action,
            AuditTarget target,
            Map<String, Object> details
    ) {
        if (!AuditContract.isEventId(eventId)) {
            throw new IllegalArgumentException("eventId");
        }
        Objects.requireNonNull(occurredOn, "occurredOn");
        if (!AuditContract.isSourceService(sourceService)) {
            throw new IllegalArgumentException("sourceService");
        }
        this.actor = Objects.requireNonNull(actor, "actor");
        if (!AuditContract.isAction(action)) {
            throw new IllegalArgumentException("action");
        }
        this.target = Objects.requireNonNull(target, "target");

        this.eventId = eventId;
        this.occurredOn = occurredOn;
        this.sourceService = sourceService;
        this.action = action;
        this.details = details == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(details));
    }

    @Override
    public String getEventId() {
        return eventId;
    }

    @Override
    public Instant getOccurredOn() {
        return occurredOn;
    }

    @Override
    public String getAggregateId() {
        return target.id();
    }

    @Override
    public String getAggregateType() {
        return target.type();
    }

    @Override
    public String getRoutingKey() {
        return AuditContract.TOPIC;
    }

    @Override
    public Object getPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sourceService", sourceService);

        Map<String, Object> actorMap = new LinkedHashMap<>();
        actorMap.put("accountId", actor.accountId() != null ? actor.accountId().toString() : null);
        actorMap.put("role", actor.role());
        payload.put("actor", Collections.unmodifiableMap(actorMap));

        payload.put("action", action);

        Map<String, Object> targetMap = new LinkedHashMap<>();
        targetMap.put("type", target.type());
        targetMap.put("id", target.id());
        payload.put("target", Collections.unmodifiableMap(targetMap));

        payload.put("details", details);

        return Collections.unmodifiableMap(payload);
    }
}
