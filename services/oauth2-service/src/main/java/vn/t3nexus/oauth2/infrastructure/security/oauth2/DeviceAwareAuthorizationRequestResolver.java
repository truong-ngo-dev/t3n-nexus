package vn.t3nexus.oauth2.infrastructure.security.oauth2;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

@Slf4j
public class DeviceAwareAuthorizationRequestResolver implements OAuth2AuthorizationRequestResolver {

    static final String COOKIE_DEVICE_HASH   = "dh";
    static final String SESSION_DEVICE_HASH  = "pre_auth_device_hash";

    private final DefaultOAuth2AuthorizationRequestResolver delegate;

    public DeviceAwareAuthorizationRequestResolver(ClientRegistrationRepository repo) {
        this.delegate = new DefaultOAuth2AuthorizationRequestResolver(repo, "/oauth2/authorization");
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
        OAuth2AuthorizationRequest authRequest = delegate.resolve(request);
        if (authRequest != null) captureDeviceHash(request);
        return authRequest;
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String clientRegistrationId) {
        OAuth2AuthorizationRequest authRequest = delegate.resolve(request, clientRegistrationId);
        if (authRequest != null) captureDeviceHash(request);
        return authRequest;
    }

    private void captureDeviceHash(HttpServletRequest request) {
        String deviceHash = extractCookie(request, COOKIE_DEVICE_HASH);
        if (deviceHash == null || deviceHash.isBlank()) {
            log.info("[DeviceResolver] No device hash cookie — compositeHash will rely on userAgent+acceptLanguage only");
            return;
        }
        HttpSession session = request.getSession(true);
        session.setAttribute(SESSION_DEVICE_HASH, deviceHash);
        log.info("[DeviceResolver] device hash captured: hash={}", deviceHash);
    }

    private static String extractCookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName())) return cookie.getValue();
        }
        return null;
    }
}
