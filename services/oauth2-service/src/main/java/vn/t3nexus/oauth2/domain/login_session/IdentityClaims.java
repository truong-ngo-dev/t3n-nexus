package vn.t3nexus.oauth2.domain.login_session;

import vn.t3nexus.oauth2.domain.user_account.Role;
import vn.t3nexus.oauth2.domain.user_account.UserAccountId;

import java.time.Instant;

/**
 * Hợp đồng danh tính (giá trị miền): mã tài khoản, vai, {@code sid} (mã phiên ứng dụng), hạn. Không có email, tên,
 * mã cookie đăng nhập, {@code authorization_id} (ADR-0003 mục 4). Hạ tầng chỉ ký và mã hóa nó thành mã đăng nhập.
 */
public record IdentityClaims(UserAccountId accountId, Role role, ClientSessionId sid, Instant expiresAt) {

    public IdentityClaims {
        if (accountId == null || role == null || sid == null || expiresAt == null) {
            throw new IllegalArgumentException("accountId, role, sid and expiresAt are required");
        }
    }
}
