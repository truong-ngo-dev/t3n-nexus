package vn.t3nexus.oauth2.infrastructure.security.session;

/** Tên thuộc tính của session HTTP (Spring Session) do Xác thực đặt. */
public final class LoginSessionAttributes {

    /**
     * Mã phiên đăng nhập của ta, đặt lúc mở phiên (bộ xử lý thành công) và đọc ở cửa kiểm hạn của {@code /oauth2/authorize}.
     * Là mã của ta, không phải mã session của Spring.
     */
    public static final String LOGIN_SESSION_ID = "login_session_id";

    private LoginSessionAttributes() {
    }
}
