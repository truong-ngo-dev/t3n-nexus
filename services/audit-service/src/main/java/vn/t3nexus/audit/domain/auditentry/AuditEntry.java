package vn.t3nexus.audit.domain.auditentry;

import vn.t3nexus.lib.audit.AuditActor;
import vn.t3nexus.lib.audit.AuditRecord;
import vn.t3nexus.lib.audit.AuditTarget;
import vn.t3nexus.lib.common.domain.model.AbstractAggregateRoot;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Gốc bất biến đại diện cho một bản ghi kiểm toán trong miền (INV-AUD-01); không phát sự kiện miền.
 * <br>Bảo đảm dữ liệu không bị thay đổi sau khi được ghi nhận.
 */
public final class AuditEntry extends AbstractAggregateRoot<AuditEntryId> {

    private final String sourceEventId;
    private final Instant occurredAt;
    private final Instant recordedAt;
    private final AuditActor actor;
    private final String action;
    private final AuditTarget target;
    private final Map<String, Object> details;
    private final String sourceService;
    private final String traceId;

    private AuditEntry(
            UUID id,
            String sourceEventId,
            Instant occurredAt,
            Instant recordedAt,
            AuditActor actor,
            String action,
            AuditTarget target,
            Map<String, Object> details,
            String sourceService,
            String traceId
    ) {
        super.setId(AuditEntryId.of(id));
        this.sourceEventId = sourceEventId;
        this.occurredAt = occurredAt;
        this.recordedAt = recordedAt;
        this.actor = actor;
        this.action = action;
        this.target = target;
        this.details = details;
        this.sourceService = sourceService;
        this.traceId = traceId;
    }

    /**
     * Ghi nhận một bản ghi kiểm toán mới từ sự kiện nguồn.
     * <br>occurredAt và recordedAt được cắt về micro giây vì cơ sở dữ liệu lưu micro giây và con trỏ so sánh chính xác.
     */
    public static AuditEntry record(UUID id, Instant recordedAt, AuditRecord record) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(recordedAt, "recordedAt");
        Objects.requireNonNull(record, "record");

        Instant truncatedOccurredAt = record.occurredAt().truncatedTo(ChronoUnit.MICROS);
        Instant truncatedRecordedAt = recordedAt.truncatedTo(ChronoUnit.MICROS);
        Map<String, Object> unmodifiableDetails = Collections.unmodifiableMap(
                new LinkedHashMap<>(record.details() != null ? record.details() : Map.of())
        );

        return new AuditEntry(
                id,
                record.sourceEventId(),
                truncatedOccurredAt,
                truncatedRecordedAt,
                record.actor(),
                record.action(),
                record.target(),
                unmodifiableDetails,
                record.sourceService(),
                record.traceId()
        );
    }

    /**
     * Dựng lại bản ghi kiểm toán từ lưu trữ.
     * <br>Không cắt micro giây vì dữ liệu đọc từ cơ sở dữ liệu đã ở độ phân giải micro giây.
     */
    public static AuditEntry restore(
            UUID id,
            String sourceEventId,
            Instant occurredAt,
            Instant recordedAt,
            AuditActor actor,
            String action,
            AuditTarget target,
            Map<String, Object> details,
            String sourceService,
            String traceId
    ) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(sourceEventId, "sourceEventId");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(recordedAt, "recordedAt");
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(sourceService, "sourceService");

        Map<String, Object> unmodifiableDetails = Collections.unmodifiableMap(
                new LinkedHashMap<>(details != null ? details : Map.of())
        );

        return new AuditEntry(
                id,
                sourceEventId,
                occurredAt,
                recordedAt,
                actor,
                action,
                target,
                unmodifiableDetails,
                sourceService,
                traceId
        );
    }

    public UUID id() {
        return getId().getValue();
    }

    /** Bản ghi bất biến (INV-AUD-01): mã chỉ được gán một lần lúc dựng. */
    @Override
    public void setId(AuditEntryId id) {
        throw new UnsupportedOperationException("Bản ghi kiểm toán là bất biến: không đổi mã");
    }

    public String sourceEventId() {
        return sourceEventId;
    }

    public Instant occurredAt() {
        return occurredAt;
    }

    public Instant recordedAt() {
        return recordedAt;
    }

    public AuditActor actor() {
        return actor;
    }

    public String action() {
        return action;
    }

    public AuditTarget target() {
        return target;
    }

    public Map<String, Object> details() {
        return details;
    }

    public String sourceService() {
        return sourceService;
    }

    public String traceId() {
        return traceId;
    }
}
