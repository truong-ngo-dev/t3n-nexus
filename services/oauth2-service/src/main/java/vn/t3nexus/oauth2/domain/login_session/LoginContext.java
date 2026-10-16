package vn.t3nexus.oauth2.domain.login_session;

/** Thông tin của yêu cầu đăng nhập, chỉ đi trong event (không lưu trong phiên). */
public record LoginContext(String ipAddress, String userAgent) {
}
