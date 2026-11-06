package vn.t3nexus.audit.presentation.auditentry;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Chi tiết bản ghi kiểm toán, bao gồm sourceEventId, details và traceId.
 */
public record AuditEntryDetailResponse(
        UUID id,
        Instant occurredAt,
        Instant recordedAt,
        ActorResponse actor,
        String action,
        TargetResponse target,
        String sourceService,
        String sourceEventId,
        Map<String, Object> details,
        String traceId
) {
}
