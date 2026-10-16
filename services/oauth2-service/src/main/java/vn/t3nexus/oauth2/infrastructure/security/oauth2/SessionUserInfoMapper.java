package vn.t3nexus.oauth2.infrastructure.security.oauth2;

import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.oidc.authentication.OidcUserInfoAuthenticationContext;

import java.util.function.Function;

/**
 * Nội dung của endpoint OIDC {@code /userinfo}: trả toàn bộ claim của access token (kể cả claim tùy biến như {@code sid},
 * {@code role}). Là một phần cấu hình của khái niệm userinfo (đối tượng do OIDC sở hữu), nên nằm ở gói {@code oauth2}.
 */
public class SessionUserInfoMapper implements Function<OidcUserInfoAuthenticationContext, OidcUserInfo> {

    @Override
    public OidcUserInfo apply(OidcUserInfoAuthenticationContext context) {
        OAuth2Authorization authorization = context.getAuthorization();
        OAuth2Authorization.Token<OAuth2AccessToken> accessToken = authorization.getToken(OAuth2AccessToken.class);
        if (accessToken == null || accessToken.getClaims() == null) {
            throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_TOKEN);
        }
        return new OidcUserInfo(accessToken.getClaims());
    }
}
