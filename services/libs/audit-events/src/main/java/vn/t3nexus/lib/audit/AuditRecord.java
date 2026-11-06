package vn.t3nexus.lib.audit;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Bản ghi kiểm toán đầy đủ sau khi được phân tích từ khung sự kiện.
 *
 * @param sourceEventId  mã sự kiện gốc.
 * @param occurredAt     thời điểm xảy ra sự kiện.
 * @param traceId        mã vết phân tán (null nếu rỗng).
 * @param sourceService  tên dịch vụ phát sinh sự kiện.
 * @param actor          chủ thể thực hiện.
 * @param action         hành động nghiệp vụ.
 * @param target         đối tượng chịu tác động.
 * @param details        các dữ kiện được giữ lại.
 * @param droppedDetails danh sách các dữ kiện bị loại bỏ do không thỏa chính sách.
 */
public record AuditRecord(
        String sourceEventId,
        Instant occurredAt,
        String traceId,
        String sourceService,
        AuditActor actor,
        String action,
        AuditTarget target,
        Map<String, Object> details,
        List<AuditDetailsPolicy.Dropped> droppedDetails
) {
}
