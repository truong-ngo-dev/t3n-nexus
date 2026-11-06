package vn.t3nexus.lib.audit;

import vn.t3nexus.lib.events.SensitiveFieldPolicy;

import java.util.Set;

/**
 * Khai báo một loại hành động kiểm toán và tập các khóa dữ kiện bổ sung được phép đi kèm.
 *
 * @param action    tên hành động, ví dụ SELLER_PROFILE_REJECTED.
 * @param detailKeys tập các khóa dữ kiện được phép cho hành động này.
 */
public record AuditActionDeclaration(String action, Set<String> detailKeys) {

    public AuditActionDeclaration {
        if (!AuditContract.isAction(action)) {
            throw new IllegalArgumentException("action");
        }
        Set<String> keys = detailKeys == null ? Set.of() : Set.copyOf(detailKeys);
        SensitiveFieldPolicy policy = SensitiveFieldPolicy.strict();
        for (String k : keys) {
            if (!AuditContract.isDetailKey(k)) {
                throw new IllegalArgumentException(k);
            }
            if (policy.isForbidden(k)) {
                throw new IllegalArgumentException(k);
            }
        }
        detailKeys = keys;
    }
}
