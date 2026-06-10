package vn.t3nexus.oauth2.infrastructure.security.handler;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import vn.t3nexus.oauth2.application.user_credential.publish_login_failed.PublishLoginFailed;
import vn.t3nexus.oauth2.infrastructure.cross_cutting.utils.IpAddressExtractor;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class SocialLoginFailureHandler implements AuthenticationFailureHandler {

    private final PublishLoginFailed publishLoginFailed;

    @Override
    public void onAuthenticationFailure(@NotNull HttpServletRequest request,
                                        @NotNull HttpServletResponse response,
                                        @NotNull AuthenticationException exception) throws IOException {
        String ipAddress      = IpAddressExtractor.extract(request);
        String userAgent      = request.getHeader("User-Agent");
        String acceptLanguage = request.getHeader("Accept-Language");

        HttpSession session = request.getSession(false);
        String deviceHash   = session != null ? (String) session.getAttribute("pre_auth_device_hash") : null;
        if (session != null) {
            session.removeAttribute("pre_auth_device_hash");
        }

        if (exception instanceof OAuth2AuthenticationException oauthEx) {
            String errorCode = oauthEx.getError().getErrorCode();

            if ("account_locked".equals(errorCode)) {
                String email = oauthEx.getError().getDescription();
                log.warn("[LoginFailure/GOOGLE] email={}, result=ACCOUNT_LOCKED, ip={}", email, ipAddress);
                publishLoginFailed.publish(email, "ACCOUNT_LOCKED", deviceHash, acceptLanguage, ipAddress, userAgent, "GOOGLE");
                response.sendRedirect("/login?locked");
                return;
            }

            // internal_error đã được log trong SocialLoginOidcUserService, bỏ qua để tránh duplicate
            if (!"internal_error".equals(errorCode)) {
                log.warn("[LoginFailure/GOOGLE] errorCode={}, ip={}", errorCode, ipAddress);
            }
        } else {
            log.warn("[LoginFailure/GOOGLE] exception={}, ip={}", exception.getClass().getSimpleName(), ipAddress);
        }

        response.sendRedirect("/login?error");
    }
}
