package vn.t3nexus.lib.audit;

/**
 * Đại diện cho đối tượng bị tác động bởi hành động kiểm toán.
 *
 * @param type loại đối tượng, ví dụ SELLER_PROFILE.
 * @param id   mã định danh đối tượng, không rỗng và tối đa 100 ký tự.
 */
public record AuditTarget(String type, String id) {

    public AuditTarget {
        if (!AuditContract.isTargetType(type)) {
            throw new IllegalArgumentException("Đối tượng không hợp lệ: type");
        }
        if (!AuditContract.isTargetId(id)) {
            throw new IllegalArgumentException("Đối tượng không hợp lệ: id");
        }
    }
}
