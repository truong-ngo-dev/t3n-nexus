package vn.t3nexus.oauth2.infrastructure.security.handler;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import vn.t3nexus.oauth2.infrastructure.cross_cutting.utils.IpAddressExtractor;
// TODO [business]: import vn.t3nexus.oauth2.application.login_activity.record.RecordLoginActivity;
// TODO [business]: import vn.t3nexus.oauth2.domain.login_activity.LoginResult;
// TODO [business]: import vn.t3nexus.oauth2.domain.device.DeviceFingerprint;

import java.io.IOException;

/**
 * Ghi LoginActivity khi đăng nhập thất bại (Phase 1).
 * Business hook: RecordLoginActivity use-case (TODO [business]).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeviceAwareAuthenticationFailureHandler implements AuthenticationFailureHandler {

    // TODO [business]: private final RecordLoginActivity recordLoginActivity;

    @Override
    @SuppressWarnings("all")
    public void onAuthenticationFailure(HttpServletRequest request,
                                        HttpServletResponse response,
                                        AuthenticationException exception) throws IOException {
        String username       = request.getParameter("username");
        String ipAddress      = IpAddressExtractor.extract(request);
        String userAgent      = request.getHeader("User-Agent");
        String deviceHash     = request.getParameter("device_hash");
        String acceptLanguage = request.getHeader("Accept-Language");

        // Không ghi activity khi user không tồn tại — tránh record rác cho brute-force
        if (exception instanceof BadCredentialsException && exception.getCause() instanceof UsernameNotFoundException) {
            response.sendRedirect("/login?error");
            return;
        }

        log.debug("[LoginFailure] username={}, exception={}, deviceHash={}, ip={}, userAgent={}",
                username, exception.getClass().getSimpleName(),
                deviceHash != null ? deviceHash.substring(0, 8) + "..." : "null",
                ipAddress, userAgent);

        /* TODO [business]:
        LoginResult result = resolveResult(exception);
        String compositeHash = DeviceFingerprint.of(
                deviceHash     != null ? deviceHash     : "",
                userAgent      != null ? userAgent      : "",
                acceptLanguage != null ? acceptLanguage : ""
        ).getCompositeHash();
        recordLoginActivity.handle(new RecordLoginActivity.Command(
                username, compositeHash, userAgent, ipAddress, result));
        */

        if (exception instanceof LockedException || exception instanceof DisabledException) {
            response.sendRedirect("/login?locked");
        } else {
            response.sendRedirect("/login?error");
        }
    }

    /* TODO [business]:
    private LoginResult resolveResult(AuthenticationException exception) {
        if (exception instanceof LockedException || exception instanceof DisabledException) {
            return LoginResult.FAILED_ACCOUNT_LOCKED;
        }
        return LoginResult.FAILED_WRONG_PASSWORD;
    }
    */
}
