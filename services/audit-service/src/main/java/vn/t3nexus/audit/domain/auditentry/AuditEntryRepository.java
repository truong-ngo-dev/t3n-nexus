package vn.t3nexus.audit.domain.auditentry;

import vn.t3nexus.lib.common.domain.service.Repository;

/**
 * Cổng kho của miền cho bản ghi kiểm toán (đọc theo mã kế thừa từ {@link Repository}). Việc tìm kiếm có bộ lọc và
 * phân trang nằm ở cổng truy vấn của tầng ứng dụng ({@code AuditEntryQuery}).
 * <br>Bảng chỉ ghi thêm (append-only): {@link #save} chỉ thêm, {@link #delete} không được hỗ trợ (INV-AUD-01).
 */
public interface AuditEntryRepository extends Repository<AuditEntry, AuditEntryId> {

    /**
     * Thêm mới một bản ghi kiểm toán nếu mã sự kiện nguồn chưa từng xuất hiện.
     *
     * @param entry bản ghi kiểm toán cần lưu
     * @return true nếu đã thêm thành công, false nếu mã sự kiện nguồn đã tồn tại
     */
    boolean insertIfAbsent(AuditEntry entry);

    /** Chỉ thêm: bản ghi trùng mã sự kiện nguồn được bỏ qua êm (không sửa bản ghi cũ). */
    @Override
    default void save(AuditEntry entry) {
        insertIfAbsent(entry);
    }

    /** Không hỗ trợ: nhật ký không xóa được (INV-AUD-01, ADR-0009). */
    @Override
    default void delete(AuditEntryId id) {
        throw new UnsupportedOperationException("Nhật ký kiểm toán không xóa được (INV-AUD-01)");
    }
}
