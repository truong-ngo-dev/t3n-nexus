package vn.t3nexus.oauth2.infrastructure.security.session;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

import java.util.Optional;

/** Cookie mã thiết bị {@code DEVICE_KEY} (ADR-0005): giá trị thô chỉ được đọc để băm, không lưu và không đi tiếp. */
public final class DeviceKeyCookie {

    public static final String NAME = "DEVICE_KEY";

    private DeviceKeyCookie() {
    }

    public static Optional<String> read(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return Optional.empty();
        for (Cookie cookie : cookies) {
            if (NAME.equals(cookie.getName()) && cookie.getValue() != null && !cookie.getValue().isBlank()) {
                return Optional.of(cookie.getValue());
            }
        }
        return Optional.empty();
    }
}
