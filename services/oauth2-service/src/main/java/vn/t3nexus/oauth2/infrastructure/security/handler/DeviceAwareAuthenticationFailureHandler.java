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
import vn.t3nexus.oauth2.application.user_credential.publish_login_failed.PublishLoginFailed;
import vn.t3nexus.oauth2.infrastructure.cross_cutting.utils.IpAddressExtractor;

import java.io.IOException;

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
            response.sendRedirect("/login?error");
            return;
        }

        log.info("[LoginFailure] username={}, exception={}, ip={}", username, exception.getClass().getSimpleName(), ipAddress);

        publishLoginFailed.publish(username, resolveResult(exception), deviceHash, acceptLanguage, ipAddress, userAgent, "LOCAL");

        if (exception instanceof LockedException || exception instanceof DisabledException) {
            response.sendRedirect("/login?locked");
        } else {
            response.sendRedirect("/login?error");
        }
    }

    private String resolveResult(AuthenticationException exception) {
        if (exception instanceof LockedException || exception instanceof DisabledException) {
            return "ACCOUNT_LOCKED";
        }
        return "WRONG_PASSWORD";
    }
}
