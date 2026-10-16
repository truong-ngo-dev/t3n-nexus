package vn.t3nexus.oauth2.infrastructure.security.oauth2.token;

import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import vn.t3nexus.oauth2.domain.user_account.Role;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Phần phiên đăng nhập của ta mang theo bản ghi ủy quyền ({@code oauth2_authorization.attributes}), ghi lúc cấp mã
 * (ADR-0003 mục 3): mã phiên người dùng, mã phiên ứng dụng ({@code sid}, chốt từ lúc cấp mã vì JWT ký trước khi điểm móc
 * lúc token được cấp chạy), hạn phiên (bất biến) và vai. Bộ tùy biến JWT và bộ sinh refresh token đọc từ đây để đặt
 * {@code sid} và cắt hạn mà không đọc bảng phiên.
 *
 * <p>Không mang mã cookie của Spring ({@code SESSION_ID}) và không mang {@code authorization_id}: chúng không bao giờ rời
 * cơ sở dữ liệu của Xác thực (ADR-0003 mục 4). Hạn lưu dạng chuỗi ISO-8601 để không phụ thuộc kiểu được phép của bộ đọc JSON.
 */
public record SessionBinding(String loginSessionId, String sid, Instant sessionExpiresAt, Role role) {

    public static final String LOGIN_SESSION_ID  = "login_session_id";
    public static final String CLIENT_SESSION_ID = "client_session_id";
    public static final String SESSION_EXPIRES_AT = "session_expires_at";
    public static final String ROLE              = "account_role";

    public SessionBinding {
        if (loginSessionId == null || sid == null || sessionExpiresAt == null || role == null) {
            throw new IllegalArgumentException("loginSessionId, sid, sessionExpiresAt and role are required");
        }
    }

    /** Thuộc tính cần ghi vào bản ghi ủy quyền. */
    public Map<String, Object> toAttributes() {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put(LOGIN_SESSION_ID, loginSessionId);
        attributes.put(CLIENT_SESSION_ID, sid);
        attributes.put(SESSION_EXPIRES_AT, sessionExpiresAt.toString());
        attributes.put(ROLE, role.name());
        return attributes;
    }

    /**
     * Đọc từ bản ghi ủy quyền. Rỗng khi bản ghi không thuộc luồng đăng nhập của người dùng (ví dụ client credentials giữa
     * các dịch vụ) hoặc thiếu thuộc tính.
     */
    public static Optional<SessionBinding> from(OAuth2Authorization authorization) {
        if (authorization == null) return Optional.empty();
        String loginSessionId = authorization.getAttribute(LOGIN_SESSION_ID);
        String sid            = authorization.getAttribute(CLIENT_SESSION_ID);
        String expiresAt      = authorization.getAttribute(SESSION_EXPIRES_AT);
        String role           = authorization.getAttribute(ROLE);
        if (loginSessionId == null || sid == null || expiresAt == null || role == null) return Optional.empty();
        return Optional.of(new SessionBinding(loginSessionId, sid, Instant.parse(expiresAt), Role.valueOf(role)));
    }
}
