package vn.t3nexus.audit.application.auditentry;

import vn.t3nexus.audit.domain.auditentry.AuditEntry;

import java.util.List;

/**
 * Cổng tìm kiếm bản ghi kiểm toán (phía truy vấn), tách khỏi cổng ghi của miền ({@code AuditEntryRepository}).
 * <br>Chỉ đọc: không có thao tác nào đổi dữ liệu (INV-AUD-01).
 */
public interface AuditEntryQuery {

    /**
     * Tìm kiếm và phân trang bản ghi kiểm toán theo bộ lọc và con trỏ.
     *
     * @param filter bộ lọc tiêu chí tra cứu
     * @param after  con trỏ vị trí bắt đầu (có thể null nếu trang đầu tiên)
     * @param limit  số lượng bản ghi tối đa cần lấy
     * @return danh sách bản ghi sắp xếp theo occurredAt giảm dần rồi id giảm dần
     */
    List<AuditEntry> search(AuditEntryFilter filter, AuditCursor after, int limit);
}
