package vn.t3nexus.identity.domain.login_activity;

public enum LoginResult {
    SUCCESS,
    WRONG_PASSWORD,
    ACCOUNT_LOCKED,
    MFA_FAILED,
    // Account tồn tại + password đúng nhưng UserCredential.status = PENDING (chưa verify email) —
    // Spring Security ném DisabledException (khác LockedException), tách riêng để login history
    // phân biệt được "chưa verify" với "bị khóa" (2 nguyên nhân, 2 hướng xử lý khác nhau).
    EMAIL_NOT_VERIFIED
}
