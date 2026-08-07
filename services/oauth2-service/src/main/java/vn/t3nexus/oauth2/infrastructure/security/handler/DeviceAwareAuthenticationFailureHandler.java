package vn.t3nexus.oauth2.infrastructure.security.handler;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriUtils;
import vn.t3nexus.oauth2.application.user_credential.publish_login_failed.PublishLoginFailed;
import vn.t3nexus.oauth2.infrastructure.cross_cutting.utils.IpAddressExtractor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Slf4j
@Component
@RequiredArgsConstructor
public class DeviceAwareAuthenticationFailureHandler implements AuthenticationFailureHandler {

    private final PublishLoginFailed publishLoginFailed;

    @Override
    public void onAuthenticationFailure(HttpServletRequest request,
                                        @NotNull HttpServletResponse response,
                                        @NotNull AuthenticationException exception) throws IOException {
        String username       = request.getParameter("username");
        String ipAddress      = IpAddressExtractor.extract(request);
        String userAgent      = request.getHeader("User-Agent");
        String deviceHash     = request.getParameter("device_hash");
        String acceptLanguage = request.getHeader("Accept-Language");

        // User không tồn tại — không record, tránh noise từ brute-force trên account lạ
        if (exception instanceof BadCredentialsException && exception.getCause() instanceof UsernameNotFoundException) {
            response.sendRedirect(request.getContextPath() + "/login?error");
            return;
        }

        log.info("[LoginFailure] username={}, exception={}, ip={}", username, exception.getClass().getSimpleName(), ipAddress);

        publishLoginFailed.publish(username, resolveResult(exception), deviceHash, acceptLanguage, ipAddress, userAgent, "LOCAL");

        // LockedException (accountNonLocked=false, bị khóa thật) và DisabledException (enabled=false,
        // UserCredential.status=PENDING — chưa verify email) là 2 exception TÁCH SẴN bởi Spring
        // Security (OAuth2UserDetailsService.mapToUserDetails() map đúng 2 field khác nhau) — trước
        // đây bị gộp chung "?locked" khiến user chưa verify thấy nhầm "tài khoản bị khóa, liên hệ hỗ
        // trợ" trong khi chỉ cần tự bấm link email. Tách riêng "?unverified" kèm email để trang login
        // hiện nút gửi lại — nút gọi thẳng identity-service qua gateway bằng JS (login.html), không
        // qua backend nào của oauth2-service (xem docs/feature/02-login).
        if (exception instanceof LockedException) {
            response.sendRedirect(request.getContextPath() + "/login?locked");
        } else if (exception instanceof DisabledException) {
            response.sendRedirect(request.getContextPath() + "/login?unverified&email=" + UriUtils.encode(username, StandardCharsets.UTF_8));
        } else {
            response.sendRedirect(request.getContextPath() + "/login?error");
        }
    }

    private String resolveResult(AuthenticationException exception) {
        if (exception instanceof LockedException) {
            return "ACCOUNT_LOCKED";
        }
        if (exception instanceof DisabledException) {
            return "EMAIL_NOT_VERIFIED";
        }
        return "WRONG_PASSWORD";
    }
}
