package vn.t3nexus.audit.presentation.auditentry;

import java.time.Instant;
import java.util.UUID;

/**
 * Tóm tắt bản ghi kiểm toán trong danh sách.
 * <br>Bảo đảm không bao gồm details, traceId, sourceEventId theo thiết kế bảo mật.
 */
public record AuditEntrySummaryResponse(
        UUID id,
        Instant occurredAt,
        Instant recordedAt,
        ActorResponse actor,
        String action,
        TargetResponse target,
        String sourceService
) {
}
