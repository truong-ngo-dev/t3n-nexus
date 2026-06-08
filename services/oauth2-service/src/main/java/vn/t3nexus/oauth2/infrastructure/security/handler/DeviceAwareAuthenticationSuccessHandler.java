package vn.t3nexus.oauth2.infrastructure.security.handler;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import vn.t3nexus.oauth2.infrastructure.cross_cutting.utils.IpAddressExtractor;
import vn.t3nexus.oauth2.infrastructure.security.model.DeviceAwareWebAuthenticationDetails;

import java.io.IOException;

/**
 * Phase 1 — capture raw device signals sau khi xác thực thành công,
 * lưu vào HTTP session để Phase 1.5 copy vào OAuth2Authorization.attributes,
 * Phase 2 dùng khi issue token.
 *
 * LOCAL : signals từ DeviceAwareWebAuthenticationDetails (hidden input + headers)
 * GOOGLE: deviceHash đọc từ session attribute pre_auth_device_hash
 */
@Slf4j
@Component
public class DeviceAwareAuthenticationSuccessHandler extends SavedRequestAwareAuthenticationSuccessHandler {

    public DeviceAwareAuthenticationSuccessHandler() {
        this.setAlwaysUseDefaultTargetUrl(false);
    }

    @Override
    @SuppressWarnings("all")
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
            Authentication authentication) throws IOException, ServletException {

        if (authentication.getPrincipal() instanceof OidcUser oidcUser) {
            String email      = oidcUser.getEmail();
            String userAgent  = request.getHeader("User-Agent");
            String acceptLang = request.getHeader("Accept-Language");
            String ipAddress  = IpAddressExtractor.extract(request);

            HttpSession session = request.getSession(false);
            String deviceHash   = (String) session.getAttribute("pre_auth_device_hash");
            session.removeAttribute("pre_auth_device_hash");

            session.setAttribute("auth_email",           email);
            session.setAttribute("auth_device_hash",     deviceHash);
            session.setAttribute("auth_user_agent",      userAgent);
            session.setAttribute("auth_accept_language", acceptLang);
            session.setAttribute("auth_ip_address",      ipAddress);
            session.setAttribute("auth_provider",        "GOOGLE");

            log.info("[LoginSuccess/GOOGLE] email={}, deviceHash={}, ip={}, userAgent={}",
                    email,
                    deviceHash != null ? deviceHash.substring(0, 8) + "..." : "null",
                    ipAddress, userAgent);

        } else if (authentication.getDetails() instanceof DeviceAwareWebAuthenticationDetails details) {
            String email      = request.getParameter("username");
            String deviceHash = details.getDeviceHash();
            String userAgent  = details.getUserAgent();
            String acceptLang = details.getAcceptLanguage();
            String ipAddress  = details.getIpAddress();

            HttpSession session = request.getSession(false);
            session.setAttribute("auth_email",           email);
            session.setAttribute("auth_device_hash",     deviceHash);
            session.setAttribute("auth_user_agent",      userAgent);
            session.setAttribute("auth_accept_language", acceptLang);
            session.setAttribute("auth_ip_address",      ipAddress);
            session.setAttribute("auth_provider",        "LOCAL");

            log.info("[LoginSuccess/LOCAL] email={}, deviceHash={}, ip={}, userAgent={}",
                    email,
                    deviceHash != null ? deviceHash.substring(0, 8) + "..." : "null",
                    ipAddress, userAgent);
        } else {
            log.warn("[LoginSuccess] Unknown authentication type — skipping device tracking");
        }

        super.onAuthenticationSuccess(request, response, authentication);
    }
}
