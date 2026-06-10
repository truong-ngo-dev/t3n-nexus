package vn.t3nexus.oauth2.infrastructure.security.handler;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import vn.t3nexus.oauth2.application.user_credential.publish_login_failed.PublishLoginFailed;
import vn.t3nexus.oauth2.infrastructure.cross_cutting.utils.IpAddressExtractor;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class OttAuthenticationFailureHandler implements AuthenticationFailureHandler {

    private final PublishLoginFailed publishLoginFailed;

    @Override
    public void onAuthenticationFailure(HttpServletRequest request,
                                        @NotNull HttpServletResponse response,
                                        @NotNull AuthenticationException exception) throws IOException {
        HttpSession session = request.getSession(false);
        if (session != null) {
            String email         = (String) session.getAttribute("auth_email");
            String deviceHash    = (String) session.getAttribute("auth_device_hash");
            String acceptLang    = (String) session.getAttribute("auth_accept_language");
            String ipAddress     = (String) session.getAttribute("auth_ip_address");
            String userAgent     = request.getHeader("User-Agent");
            String provider      = (String) session.getAttribute("auth_provider");

            if (email != null) {
                log.info("[MfaFailure] email={}, ip={}", email, IpAddressExtractor.extract(request));
                publishLoginFailed.publish(email, "MFA_FAILED", deviceHash, acceptLang, ipAddress, userAgent,
                        provider != null ? provider : "LOCAL");
            }
        }

        response.sendRedirect("/mfa/verify?error");
    }
}
