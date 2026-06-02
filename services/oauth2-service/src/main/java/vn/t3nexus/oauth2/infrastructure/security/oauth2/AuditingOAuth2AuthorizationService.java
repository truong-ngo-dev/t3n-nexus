package vn.t3nexus.oauth2.infrastructure.security.oauth2;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationCode;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
// TODO [business]: import vn.t3nexus.oauth2.application.auth.complete_login.CompleteLogin;

/**
 * Wraps JdbcOAuth2AuthorizationService để hook vào login flow:
 *
 * Phase 1.5 — Authorization Code Issued (browser request, có HTTP session):
 *   Copy device info từ HTTP session vào OAuth2Authorization.attributes.
 *
 * Phase 2 — Token Issuance (server-to-server, không có HTTP session):
 *   TODO [business]: Gọi CompleteLogin — tạo OAuthSession + ghi LoginActivity(SUCCESS).
 */
@Slf4j
@RequiredArgsConstructor
public class AuditingOAuth2AuthorizationService implements OAuth2AuthorizationService {

    private static final String ATTR_DEVICE_ID      = "auth_device_id";
    private static final String ATTR_IP_ADDRESS     = "auth_ip_address";
    private static final String ATTR_USER_AGENT     = "auth_user_agent";
    private static final String ATTR_COMPOSITE_HASH = "auth_composite_hash";
    private static final String ATTR_USERNAME       = "auth_username";
    private static final String ATTR_IDP_SESSION_ID = "auth_idp_session_id";

    private final OAuth2AuthorizationService delegate;
    // TODO [business]: private final CompleteLogin completeLogin;

    @Override
    public void save(OAuth2Authorization authorization) {
        // Phase 1.5: authorization code just issued — attach device info from HTTP session
        if (hasAuthorizationCode(authorization) && !hasAccessToken(authorization)
                && authorization.getAttribute(ATTR_DEVICE_ID) == null) {
            authorization = attachDeviceInfoFromSession(authorization);
        }

        delegate.save(authorization);

        /* TODO [business]: Phase 2 — access token just issued — complete the login flow
        if (hasAccessToken(authorization) && authorization.getAttribute(ATTR_DEVICE_ID) != null) {
            String userId        = authorization.getPrincipalName();
            String deviceId      = authorization.getAttribute(ATTR_DEVICE_ID);
            String ipAddress     = authorization.getAttribute(ATTR_IP_ADDRESS);
            String userAgent     = authorization.getAttribute(ATTR_USER_AGENT);
            String compositeHash = authorization.getAttribute(ATTR_COMPOSITE_HASH);
            String username      = authorization.getAttribute(ATTR_USERNAME);
            String idpSessionId  = authorization.getAttribute(ATTR_IDP_SESSION_ID);
            String authorizationId = authorization.getId();

            completeLogin.handle(new CompleteLogin.Command(
                    userId, username, deviceId, compositeHash, userAgent, ipAddress, authorizationId, idpSessionId));
        }
        */
    }

    @Override
    public void remove(OAuth2Authorization authorization) {
        delegate.remove(authorization);
    }

    @Override
    public OAuth2Authorization findById(String id) {
        return delegate.findById(id);
    }

    @Override
    public OAuth2Authorization findByToken(String token, OAuth2TokenType tokenType) {
        return delegate.findByToken(token, tokenType);
    }

    private OAuth2Authorization attachDeviceInfoFromSession(OAuth2Authorization authorization) {
        try {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
            HttpSession session = attrs.getRequest().getSession(false);
            if (session == null) return authorization;
            String deviceId = (String) session.getAttribute(ATTR_DEVICE_ID);
            if (deviceId == null) return authorization;
            return OAuth2Authorization.from(authorization)
                    .attribute(ATTR_DEVICE_ID,      deviceId)
                    .attribute(ATTR_IP_ADDRESS,     session.getAttribute(ATTR_IP_ADDRESS))
                    .attribute(ATTR_USER_AGENT,     session.getAttribute(ATTR_USER_AGENT))
                    .attribute(ATTR_COMPOSITE_HASH, session.getAttribute(ATTR_COMPOSITE_HASH))
                    .attribute(ATTR_USERNAME,       session.getAttribute(ATTR_USERNAME))
                    .attribute(ATTR_IDP_SESSION_ID, session.getId())
                    .build();
        } catch (IllegalStateException e) {
            return authorization;
        }
    }

    private boolean hasAuthorizationCode(OAuth2Authorization authorization) {
        return authorization.getToken(OAuth2AuthorizationCode.class) != null;
    }

    private boolean hasAccessToken(OAuth2Authorization authorization) {
        return authorization.getToken(OAuth2AccessToken.class) != null;
    }
}
