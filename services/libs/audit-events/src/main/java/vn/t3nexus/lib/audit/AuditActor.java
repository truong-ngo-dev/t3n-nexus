package vn.t3nexus.lib.audit;

import java.util.UUID;

/**
 * Đại diện cho chủ thể thực hiện hành động kiểm toán.
 *
 * @param accountId định danh tài khoản, chỉ được phép null khi vai trò là SYSTEM.
 * @param role      vai trò thực hiện hành động, tuân theo quy tắc đặt tên vai trò.
 */
public record AuditActor(UUID accountId, String role) {

    public AuditActor {
        if (!AuditContract.isRole(role)) {
            throw new IllegalArgumentException("Người làm không hợp lệ: role");
        }
        if (accountId == null) {
            if (!AuditContract.SYSTEM_ROLE.equals(role)) {
                throw new IllegalArgumentException("Người làm không hợp lệ: accountId");
            }
        }
    }
}
