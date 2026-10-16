package vn.t3nexus.oauth2.infrastructure.security.session;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import vn.t3nexus.oauth2.domain.login_session.DeviceKeyGenerator;

import java.util.Optional;

/**
 * Cấp và gia hạn cookie mã thiết bị {@code DEVICE_KEY} (ADR-0005): ngẫu nhiên 256 bit, {@code HttpOnly}, {@code Secure}
 * (cấu hình được), {@code SameSite=Lax}, {@code Path=/}, hạn 12 tháng. Cấp khi trình duyệt vào trang đăng nhập mà chưa có cookie,
 * <b>trước</b> khi nhập mật khẩu, để đăng nhập thất bại cũng mang được mã; gia hạn mỗi lần đăng nhập thành công. Cookie mất thì
 * lần đăng nhập sau là thiết bị mới. Giá trị thô chỉ ở cookie; phiên và event chỉ giữ bản băm.
 */
@Component
public class DeviceKeyCookieIssuer {

    private final DeviceKeyGenerator          generator;
    private final DeviceKeyCookieProperties   properties;

    public DeviceKeyCookieIssuer(DeviceKeyGenerator generator, DeviceKeyCookieProperties properties) {
        this.generator  = generator;
        this.properties = properties;
    }

    /** Cấp cookie mới nếu yêu cầu chưa có; đã có thì không đổi. Trả về giá trị thô của cookie hiện hành. */
    public String issueIfMissing(HttpServletRequest request, HttpServletResponse response) {
        Optional<String> existing = DeviceKeyCookie.read(request);
        if (existing.isPresent()) return existing.get();
        String value = generator.generate();
        write(response, value);
        return value;
    }

    /** Gia hạn cookie hiện có (cùng giá trị, hạn mới). Không có cookie thì không làm gì. */
    public void renew(HttpServletRequest request, HttpServletResponse response) {
        DeviceKeyCookie.read(request).ifPresent(value -> write(response, value));
    }

    private void write(HttpServletResponse response, String value) {
        ResponseCookie cookie = ResponseCookie.from(DeviceKeyCookie.NAME, value)
                .httpOnly(true)
                .secure(properties.secure())
                .sameSite("Lax")
                .path("/")
                .maxAge(properties.maxAge())
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
