package vn.t3nexus.audit.application.auditentry;

import java.time.Instant;
import java.util.UUID;

/**
 * Tiêu chí tra cứu bản ghi kiểm toán (phía đọc). Mọi trường đều tùy chọn, null biểu thị không lọc.
 */
public record AuditEntryFilter(
        Instant from,
        Instant to,
        UUID actorId,
        String action,
        String targetType,
        String targetId
) {
    public static AuditEntryFilter none() {
        return new AuditEntryFilter(null, null, null, null, null, null);
    }
}
