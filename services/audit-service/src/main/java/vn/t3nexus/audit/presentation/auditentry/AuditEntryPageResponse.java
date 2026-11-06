package vn.t3nexus.audit.presentation.auditentry;

import java.util.List;

/**
 * Phản hồi phân trang danh sách bản ghi kiểm toán.
 */
public record AuditEntryPageResponse(
        List<AuditEntrySummaryResponse> items,
        String nextCursor
) {
}
