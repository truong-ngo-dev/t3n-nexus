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
import vn.t3nexus.lib.common.domain.service.ULIDGenerator;
import vn.t3nexus.oauth2.application.session.issue_session.IssueSession;


/**
 * Wraps JdbcOAuth2AuthorizationService để hook vào login flow:
 *
 * Phase 1.5 — Authorization Code Issued (browser request, có HTTP session):
 *   Pre-generate OAuthSession ULID + copy device signals từ HTTP session vào
 *   OAuth2Authorization.attributes.
 *
 * Phase 2 — Token Issuance (server-to-server, không có HTTP session):
 *   Gọi IssueSession — tạo OAuthSession với ULID đã pre-generate từ Phase 1.5.
 */
@Slf4j
@RequiredArgsConstructor
public class AuditingOAuth2AuthorizationService implements OAuth2AuthorizationService {

    public static final  String ATTR_OAUTH_SESSION_ID = "oauth_session_id";
    private static final String ATTR_EMAIL            = "auth_email";
    private static final String ATTR_DEVICE_HASH      = "auth_device_hash";
    private static final String ATTR_USER_AGENT       = "auth_user_agent";
    private static final String ATTR_ACCEPT_LANGUAGE  = "auth_accept_language";
    private static final String ATTR_IP_ADDRESS       = "auth_ip_address";
    private static final String ATTR_IDP_SESSION_ID   = "auth_idp_session_id";
    private static final String ATTR_PROVIDER         = "auth_provider";

    private final OAuth2AuthorizationService delegate;
    private final IssueSession               issueSession;
    private final ULIDGenerator              ulidGenerator;

    @Override
    public void save(OAuth2Authorization authorization) {
        // Phase 1.5: authorization code just issued — pre-generate session ID + attach device signals
        if (hasAuthorizationCode(authorization) && !hasAccessToken(authorization)
                && authorization.getAttribute(ATTR_OAUTH_SESSION_ID) == null) {
            authorization = attachDeviceInfoFromSession(authorization);
        }

        delegate.save(authorization);

        // Phase 2: access token just issued — create OAuthSession + publish SessionIssuedEvent
        if (hasAccessToken(authorization) && authorization.getAttribute(ATTR_EMAIL) != null) {
            String oauthSessionId  = orEmpty(authorization.getAttribute(ATTR_OAUTH_SESSION_ID));
            String userId          = authorization.getPrincipalName();
            String idpSessionId    = authorization.getAttribute(ATTR_IDP_SESSION_ID);
            String authorizationId = authorization.getId();
            String ipAddress       = orEmpty(authorization.getAttribute(ATTR_IP_ADDRESS));
            String loginIdentifier = orEmpty(authorization.getAttribute(ATTR_EMAIL));
            String deviceHash      = orEmpty(authorization.getAttribute(ATTR_DEVICE_HASH));
            String userAgent       = orEmpty(authorization.getAttribute(ATTR_USER_AGENT));
            String acceptLanguage  = orEmpty(authorization.getAttribute(ATTR_ACCEPT_LANGUAGE));
            String provider        = orEmpty(authorization.getAttribute(ATTR_PROVIDER));

            issueSession.handle(new IssueSession.Command(
                    oauthSessionId, userId, idpSessionId, authorizationId, ipAddress,
                    loginIdentifier, deviceHash, userAgent, acceptLanguage, provider
            ));
        }
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

    /**
     * Luôn pre-generate OAuthSession ULID. Nếu HTTP session có device info thì attach thêm.
     * Social login (email null trong session) vẫn nhận được ULID để JWT có oss_id claim.
     */
    private OAuth2Authorization attachDeviceInfoFromSession(OAuth2Authorization authorization) {
        String preGeneratedSessionId = ulidGenerator.generate();
        try {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
            HttpSession session = attrs.getRequest().getSession(false);

            if (session == null) {
                return OAuth2Authorization.from(authorization)
                        .attribute(ATTR_OAUTH_SESSION_ID, preGeneratedSessionId)
                        .build();
            }

            String email = (String) session.getAttribute(ATTR_EMAIL);
            if (email == null) {
                return OAuth2Authorization.from(authorization)
                        .attribute(ATTR_OAUTH_SESSION_ID, preGeneratedSessionId)
                        .build();
            }

            return OAuth2Authorization.from(authorization)
                    .attribute(ATTR_OAUTH_SESSION_ID,   preGeneratedSessionId)
                    .attribute(ATTR_EMAIL,               email)
                    .attribute(ATTR_DEVICE_HASH,         session.getAttribute(ATTR_DEVICE_HASH))
                    .attribute(ATTR_USER_AGENT,          session.getAttribute(ATTR_USER_AGENT))
                    .attribute(ATTR_ACCEPT_LANGUAGE,     session.getAttribute(ATTR_ACCEPT_LANGUAGE))
                    .attribute(ATTR_IP_ADDRESS,          session.getAttribute(ATTR_IP_ADDRESS))
                    .attribute(ATTR_IDP_SESSION_ID,      session.getId())
                    .attribute(ATTR_PROVIDER,            session.getAttribute(ATTR_PROVIDER))
                    .build();
        } catch (IllegalStateException e) {
            return OAuth2Authorization.from(authorization)
                    .attribute(ATTR_OAUTH_SESSION_ID, preGeneratedSessionId)
                    .build();
        }
    }

    private boolean hasAuthorizationCode(OAuth2Authorization authorization) {
        return authorization.getToken(OAuth2AuthorizationCode.class) != null;
    }

    private boolean hasAccessToken(OAuth2Authorization authorization) {
        return authorization.getToken(OAuth2AccessToken.class) != null;
    }

    private static String orEmpty(Object value) {
        return value instanceof String s ? s : "";
    }
}
