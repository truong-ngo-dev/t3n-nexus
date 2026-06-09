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
import vn.t3nexus.oauth2.application.session.establish_session.EstablishSession;
import vn.t3nexus.oauth2.domain.session.OAuthSessionRepository;


/**
 * Wraps JdbcOAuth2AuthorizationService để hook vào login flow:
 *
 * Phase 1.5 — Authorization Code Issued (browser request, có HTTP session):
 *   Resolve OAuthSession ID + copy device signals từ HTTP session vào OAuth2Authorization.attributes.
 *   Silent SSO: reuse existing OAuthSession ID (idpSessionId + clientId đã có active session)
 *               → EstablishSession gọi onTokenRotated(), không publish SessionIssuedEvent.
 *   Fresh login: generate ULID mới → EstablishSession gọi IssueSession → publish SessionIssuedEvent.
 *
 * Phase 2 — Token Issuance (server-to-server, không có HTTP session):
 *   Gọi EstablishSession với ULID đã resolve từ Phase 1.5.
 */
@Slf4j
@RequiredArgsConstructor
public class SessionEstablishingAuthorizationService implements OAuth2AuthorizationService {

    public static final  String ATTR_OAUTH_SESSION_ID = "oauth_session_id";
    public static final  String ATTR_IDP_SESSION_ID   = "auth_idp_session_id";
    private static final String ATTR_EMAIL            = "auth_email";
    private static final String ATTR_DEVICE_HASH      = "auth_device_hash";
    private static final String ATTR_USER_AGENT       = "auth_user_agent";
    private static final String ATTR_ACCEPT_LANGUAGE  = "auth_accept_language";
    private static final String ATTR_IP_ADDRESS       = "auth_ip_address";
    private static final String ATTR_PROVIDER         = "auth_provider";

    private final OAuth2AuthorizationService delegate;
    private final EstablishSession           establishSession;
    private final OAuthSessionRepository     oAuthSessionRepository;
    private final ULIDGenerator              ulidGenerator;

    /**
     * Spring Authorization Server gọi OAuth2AuthorizationService.save() tại các điểm này trong lifecycle:
     * <br>
     *   1. Authorization request nhận vào — chưa có code <br>
     *   OAuth2AuthorizationCodeRequestAuthenticationProvider — khi AS nhận <br>
     *   /oauth2/authorize, lưu pending state để track request. <br>
     *   → hasAuthorizationCode = false → Phase 1.5 không trigger. <br>
     *   → Browser request, HTTP session có. <br>
     * <br>
     *   2. Authorization code issued — có code, chưa có token <br>
     *   Cùng provider trên, sau khi generate code. <br>
     *   → Phase 1.5 trigger: attach device signals + resolve oauth_session_id. <br>
     *   → Browser request, HTTP session có. <br>
     * <br>
     *   3. Token exchange — có access token <br>
     *   OAuth2AuthorizationCodeAuthenticationProvider — BFF POST /oauth2/token với code + code_verifier. <br>
     *   → Phase 2 trigger: EstablishSession. <br>
     *   → Server-to-server, không có HTTP session — dùng attributes đã bridge từ Phase 1.5. <br>
     * <br>
     *   4. Token refresh — access token mới <br>
     *   OAuth2RefreshTokenAuthenticationProvider — BFF dùng refresh token. <br>
     *   → hasAccessToken = true, ATTR_EMAIL vẫn còn trong attributes từ Phase 1.5. <br>
     *   → Phase 2 condition match → EstablishSession được gọi nhưng là no-op: <br>
     *      Spring AS update in-place OAuth2Authorization khi refresh (id không đổi) <br>
     *      → authorizationId match → early return. <br>
     *   → Server-to-server, không có HTTP session. <br>
     * */
    @Override
    public void save(OAuth2Authorization authorization) {
        // Phase 1.5: authorization code just issued — pre-generate session ID + attach device signals
        if (hasAuthorizationCode(authorization) && !hasAccessToken(authorization) && authorization.getAttribute(ATTR_OAUTH_SESSION_ID) == null) {
            authorization = attachDeviceInfoFromSession(authorization);
        }

        delegate.save(authorization);

        // Phase 2: access token just issued — create OAuthSession + publish SessionIssuedEvent
        // ATTR_EMAIL != null phân biệt authorization code flow (user login) với client credentials và các flow khác
        if (hasAccessToken(authorization) && authorization.getAttribute(ATTR_EMAIL) != null) {
            String oauthSessionId    = orEmpty(authorization.getAttribute(ATTR_OAUTH_SESSION_ID));
            String userId            = authorization.getPrincipalName();
            log.warn("[DEV] access_token for userId={} : {}", userId,
                    authorization.getToken(OAuth2AccessToken.class).getToken().getTokenValue());
            String idpSessionId      = orEmpty(authorization.getAttribute(ATTR_IDP_SESSION_ID));
            String authorizationId   = authorization.getId();
            String registeredClientId = authorization.getRegisteredClientId();
            String ipAddress         = orEmpty(authorization.getAttribute(ATTR_IP_ADDRESS));
            String loginIdentifier   = orEmpty(authorization.getAttribute(ATTR_EMAIL));
            String deviceHash        = orEmpty(authorization.getAttribute(ATTR_DEVICE_HASH));
            String userAgent         = orEmpty(authorization.getAttribute(ATTR_USER_AGENT));
            String acceptLanguage    = orEmpty(authorization.getAttribute(ATTR_ACCEPT_LANGUAGE));
            String provider          = orEmpty(authorization.getAttribute(ATTR_PROVIDER));

            establishSession.handle(new EstablishSession.Command(
                    oauthSessionId, userId, idpSessionId, authorizationId, ipAddress,
                    registeredClientId, loginIdentifier, deviceHash, userAgent, acceptLanguage, provider
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
     * Resolve OAuthSession ID và attach device signals vào authorization attributes.
     *
     * Silent SSO: (idpSessionId, registeredClientId) đã có active OAuthSession
     *   → reuse existing ID → EstablishSession sẽ gọi onTokenRotated(), không tạo session mới.
     * Fresh login: không có active OAuthSession → generate ULID mới.
     * Social login (email null): chỉ attach ULID mới để JWT có oss_id claim.
     */
    private OAuth2Authorization attachDeviceInfoFromSession(OAuth2Authorization authorization) {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
            HttpSession session = attrs.getRequest().getSession(false);
            // null defensive
            if (session == null) {
                return OAuth2Authorization.from(authorization)
                        .attribute(ATTR_OAUTH_SESSION_ID, ulidGenerator.generate())
                        .build();
            }

            String email = (String) session.getAttribute(ATTR_EMAIL);
            // null defensive
            if (email == null) {
                return OAuth2Authorization.from(authorization)
                        .attribute(ATTR_OAUTH_SESSION_ID, ulidGenerator.generate())
                        .build();
            }

            String idpSessionId       = session.getId();
            String registeredClientId = authorization.getRegisteredClientId();
            String sessionId = oAuthSessionRepository
                    .findActiveByIdpSessionAndClient(idpSessionId, registeredClientId)
                    .map(existing -> {
                        log.debug("[AuditingOAuth2] silent SSO detected — reusing ossId={}", existing.getId().getValueAsString());
                        return existing.getId().getValueAsString();
                    })
                    .orElseGet(ulidGenerator::generate);

            return OAuth2Authorization.from(authorization)
                    .attribute(ATTR_OAUTH_SESSION_ID,    sessionId)
                    .attribute(ATTR_EMAIL,               email)
                    .attribute(ATTR_DEVICE_HASH,         session.getAttribute(ATTR_DEVICE_HASH))
                    .attribute(ATTR_USER_AGENT,          session.getAttribute(ATTR_USER_AGENT))
                    .attribute(ATTR_ACCEPT_LANGUAGE,     session.getAttribute(ATTR_ACCEPT_LANGUAGE))
                    .attribute(ATTR_IP_ADDRESS,          session.getAttribute(ATTR_IP_ADDRESS))
                    .attribute(ATTR_IDP_SESSION_ID,      idpSessionId)
                    .attribute(ATTR_PROVIDER,            session.getAttribute(ATTR_PROVIDER))
                    .build();
        } catch (IllegalStateException e) {
            return OAuth2Authorization.from(authorization)
                    .attribute(ATTR_OAUTH_SESSION_ID, ulidGenerator.generate())
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
