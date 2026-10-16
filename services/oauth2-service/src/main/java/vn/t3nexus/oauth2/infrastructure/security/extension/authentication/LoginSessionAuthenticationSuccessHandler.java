package vn.t3nexus.oauth2.infrastructure.security.extension.authentication;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import vn.t3nexus.oauth2.application.login_session.open_login_session.OpenLoginSession;
import vn.t3nexus.oauth2.infrastructure.cross_cutting.utils.IpAddressExtractor;
import vn.t3nexus.oauth2.infrastructure.security.session.DeviceKeyCookie;
import vn.t3nexus.oauth2.infrastructure.security.session.DeviceKeyCookieIssuer;
import vn.t3nexus.oauth2.infrastructure.security.session.LoginSessionAttributes;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;

/**
 * Điểm cuối chuỗi xác thực của đăng nhập bằng mật khẩu (ADR-0003 mục 6): mở phiên người dùng và ghi mã phiên của ta vào session
 * HTTP. Chạy sau bước chống chiếm phiên của Spring nên {@code session.getId()} đã là giá trị cuối (lưu vào
 * {@code idp_session_id}). Không mở được phiên (tài khoản vừa bị khóa, lỗi cơ sở dữ liệu) thì hủy xác thực và session rồi quay
 * về trang đăng nhập: không để lại người dùng đã xác thực mà không có phiên của ta.
 */
@Slf4j
public class LoginSessionAuthenticationSuccessHandler extends SavedRequestAwareAuthenticationSuccessHandler {

    private final OpenLoginSession      openLoginSession;
    private final DeviceKeyCookieIssuer deviceKeyCookieIssuer;
    private final Clock                 clock;

    public LoginSessionAuthenticationSuccessHandler(OpenLoginSession openLoginSession,
                                                    DeviceKeyCookieIssuer deviceKeyCookieIssuer, Clock clock) {
        this.openLoginSession      = openLoginSession;
        this.deviceKeyCookieIssuer = deviceKeyCookieIssuer;
        this.clock                 = clock;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, @NonNull HttpServletResponse response,
                                        @NonNull Authentication authentication) throws IOException, ServletException {
        HttpSession session = request.getSession();
        try {
            OpenLoginSession.Result opened = openLoginSession.handle(new OpenLoginSession.Command(
                    authentication.getName(),
                    DeviceKeyCookie.read(request).orElse(null),
                    IpAddressExtractor.extract(request),
                    request.getHeader("User-Agent"),
                    session.getId()));
            session.setAttribute(LoginSessionAttributes.LOGIN_SESSION_ID, opened.loginSessionId());
            extendSessionToTheLoginSessionLifetime(session, opened);
        } catch (RuntimeException e) {
            log.warn("cannot open a login session after a successful authentication: {}", e.getClass().getSimpleName());
            SecurityContextHolder.clearContext();
            session.invalidate();
            response.sendRedirect(request.getContextPath() + "/login?error");
            return;
        }
        deviceKeyCookieIssuer.renew(request, response);   // KT-27: gia hạn mã thiết bị mỗi lần đăng nhập thành công
        super.onAuthenticationSuccess(request, response, authentication);
    }

    /**
     * Hạn không hoạt động của session Spring (kho {@code SPRING_SESSION}) mặc định chỉ 30 phút, trong khi hạn phiên của ta là 30 ngày
     * (người dùng) hoặc 10 giờ (nội bộ). Session đã đăng nhập phải sống tới hạn phiên (ADR-0003 mục 2); nếu không, cookie đăng nhập vô tác
     * dụng sau 30 phút không hoạt động và đăng nhập chung (SSO) bắt nhập lại mật khẩu. Hạn phiên của ta vẫn là hạn tuyệt đối, do cửa kiểm
     * hạn thi hành. Session vô danh (chưa đăng nhập) giữ mặc định 30 phút để các yêu cầu lạ không làm đầy kho.
     */
    private void extendSessionToTheLoginSessionLifetime(HttpSession session, OpenLoginSession.Result opened) {
        long seconds = Duration.between(clock.instant(), opened.expiresAt()).getSeconds();
        if (seconds > 0) session.setMaxInactiveInterval((int) Math.min(seconds, Integer.MAX_VALUE));
    }
}
