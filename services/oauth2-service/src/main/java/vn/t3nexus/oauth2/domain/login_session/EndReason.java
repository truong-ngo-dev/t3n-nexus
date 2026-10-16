package vn.t3nexus.oauth2.domain.login_session;

/** Lý do kết thúc phiên người dùng. Hết hạn không phải lý do: hết hạn suy ra từ hạn, không có event. */
public enum EndReason {
    SELF_LOGOUT,
    REMOTE_LOGOUT,
    ACCOUNT_LOCKED,
    PASSWORD_CHANGED
}
