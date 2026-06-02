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
import vn.t3nexus.oauth2.infrastructure.security.service.UserCredentialDetails;
// TODO [business]: import vn.t3nexus.oauth2.application.device.register_or_update.RegisterOrUpdateDevice;
// TODO [business]: import vn.t3nexus.oauth2.domain.device.DeviceFingerprint;

import java.io.IOException;

/**
 * Phase 1 — xử lý sau khi xác thực thành công:
 * 1. TODO [business]: Gọi RegisterOrUpdateDevice → tạo hoặc update Device
 * 2. Lưu device info vào HTTP session để Phase 2 dùng khi token được issued.
 *
 * Hỗ trợ hai luồng:
 * - LOCAL: deviceHash từ DeviceAwareWebAuthenticationDetails (hidden input device_hash)
 * - GOOGLE: deviceHash đọc từ session attribute pre_auth_device_hash
 */
@Slf4j
@Component
public class DeviceAwareAuthenticationSuccessHandler extends SavedRequestAwareAuthenticationSuccessHandler {

    // TODO [business]: private final RegisterOrUpdateDevice registerOrUpdateDevice;

    // TODO [business]: inject RegisterOrUpdateDevice via constructor
    public DeviceAwareAuthenticationSuccessHandler() {
        this.setAlwaysUseDefaultTargetUrl(false);
    }

    @Override
    @SuppressWarnings("all")
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
            Authentication authentication) throws IOException, ServletException {

        if (authentication.getPrincipal() instanceof OidcUser oidcUser) {
            // GOOGLE login — deviceHash đọc từ session (được POST bởi /login/device-hint)
            String userId       = authentication.getName();
            String username     = oidcUser.getEmail();
            String userAgent    = request.getHeader("User-Agent");
            String acceptLang   = request.getHeader("Accept-Language");
            String ipAddress    = IpAddressExtractor.extract(request);

            HttpSession session = request.getSession(true);
            String deviceHash   = (String) session.getAttribute("pre_auth_device_hash");
            session.removeAttribute("pre_auth_device_hash");

            /* TODO [business]:
            String deviceId = registerOrUpdateDevice.handle(new RegisterOrUpdateDevice.Command(
                    userId,
                    deviceHash != null ? deviceHash : "",
                    userAgent  != null ? userAgent  : "",
                    acceptLang != null ? acceptLang : "",
                    ipAddress
            ));
            String compositeHash = DeviceFingerprint.of(
                    deviceHash != null ? deviceHash : "",
                    userAgent  != null ? userAgent  : "",
                    acceptLang != null ? acceptLang : ""
            ).getCompositeHash();
            */
            String deviceId      = null; // TODO [business]: set from RegisterOrUpdateDevice
            String compositeHash = null; // TODO [business]: set from DeviceFingerprint

            session.setAttribute("auth_device_id",      deviceId);
            session.setAttribute("auth_ip_address",     ipAddress);
            session.setAttribute("auth_user_agent",     userAgent);
            session.setAttribute("auth_composite_hash", compositeHash);
            session.setAttribute("auth_username",       username);

            log.debug("[LoginSuccess/GOOGLE] userId={}, deviceId={}", userId, deviceId);

        } else if (authentication.getDetails() instanceof DeviceAwareWebAuthenticationDetails details) {
            // LOCAL login — deviceHash từ hidden input
            String userId    = authentication.getName();
            String username  = request.getParameter("username");
//            String ipAddress = details.getIpAddress();

            /* TODO [business]:
            String deviceId = registerOrUpdateDevice.handle(new RegisterOrUpdateDevice.Command(
                    userId,
                    details.getDeviceHash()     != null ? details.getDeviceHash()     : "",
                    details.getUserAgent()      != null ? details.getUserAgent()      : "",
                    details.getAcceptLanguage() != null ? details.getAcceptLanguage() : "",
                    ipAddress
            ));
            String compositeHash = DeviceFingerprint.of(
                    details.getDeviceHash()     != null ? details.getDeviceHash()     : "",
                    details.getUserAgent()      != null ? details.getUserAgent()      : "",
                    details.getAcceptLanguage() != null ? details.getAcceptLanguage() : ""
            ).getCompositeHash();
            */
            String deviceId      = null; // TODO [business]: set from RegisterOrUpdateDevice
            String compositeHash = null; // TODO [business]: set from DeviceFingerprint

            HttpSession session = request.getSession(true);
            session.setAttribute("auth_username",       username);
            // TODO [business]: uncomment when RegisterOrUpdateDevice is implemented
            // session.setAttribute("auth_device_id",      deviceId);
            // session.setAttribute("auth_ip_address",     details.getIpAddress());
            // session.setAttribute("auth_user_agent",     details.getUserAgent());
            // session.setAttribute("auth_composite_hash", compositeHash);

            log.debug("[LoginSuccess/LOCAL] userId={}, deviceId={}", userId, deviceId);
        } else {
            log.warn("[LoginSuccess] Unknown authentication type — skipping device tracking");
        }

        super.onAuthenticationSuccess(request, response, authentication);
    }
}
