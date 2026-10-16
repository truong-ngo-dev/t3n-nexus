package vn.t3nexus.oauth2.infrastructure.security.oauth2.token;

import org.springframework.security.crypto.keygen.Base64StringKeyGenerator;
import org.springframework.security.crypto.keygen.StringKeyGenerator;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;
import org.springframework.stereotype.Component;
import vn.t3nexus.oauth2.domain.login_session.SessionLifetimePolicy;

import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

/**
 * Bộ sinh refresh token riêng (ADR-0003 mục 3): bộ mặc định của thư viện lấy TTL cố định từ client, nên không cắt được theo
 * hạn phiên. Ở đây hạn = nhỏ hơn của (bây giờ + TTL refresh theo vai, hạn phiên); phiên đã hết hạn thì
 * {@code invalid_grant} (làm mới khi hết hạn phiên, KT-20).
 *
 * <p>Giữ hành vi mặc định ở hai chỗ: không cấp refresh token cho client công khai dùng mã ủy quyền; và với luồng không thuộc
 * phiên người dùng (không có {@link SessionBinding}) vẫn dùng TTL của client. Giá trị token: 96 bit ngẫu nhiên như bộ mặc định;
 * không nhúng mã nội bộ nào (làm mới tra theo {@code sid}, ADR-0003 mục 8).
 */
@Component
public class SessionBoundRefreshTokenGenerator implements OAuth2TokenGenerator<OAuth2RefreshToken> {

    private final StringKeyGenerator refreshTokenGenerator =
            new Base64StringKeyGenerator(Base64.getUrlEncoder().withoutPadding(), 96);

    private final SessionLifetimePolicy lifetimePolicy;
    private final Clock                 clock;

    public SessionBoundRefreshTokenGenerator(SessionLifetimePolicy lifetimePolicy, Clock clock) {
        this.lifetimePolicy = lifetimePolicy;
        this.clock          = clock;
    }

    @Override
    public OAuth2RefreshToken generate(OAuth2TokenContext context) {
        if (!OAuth2TokenType.REFRESH_TOKEN.equals(context.getTokenType())) return null;
        if (isPublicClientForAuthorizationCodeGrant(context)) return null; // không cấp refresh token cho client công khai

        Instant now = clock.instant();
        Optional<SessionBinding> binding = SessionBinding.from(context.getAuthorization());
        Instant expiresAt;
        if (binding.isPresent()) {
            SessionBinding b = binding.get();
            if (!b.sessionExpiresAt().isAfter(now)) {
                throw new OAuth2AuthenticationException(new OAuth2Error(OAuth2ErrorCodes.INVALID_GRANT,
                        "The login session has expired", null));
            }
            expiresAt = lifetimePolicy.refreshTokenExpiresAt(b.role(), now, b.sessionExpiresAt());
        } else {
            expiresAt = now.plus(context.getRegisteredClient().getTokenSettings().getRefreshTokenTimeToLive());
        }
        return new OAuth2RefreshToken(refreshTokenGenerator.generateKey(), now, expiresAt);
    }

    private static boolean isPublicClientForAuthorizationCodeGrant(OAuth2TokenContext context) {
        if (AuthorizationGrantType.AUTHORIZATION_CODE.equals(context.getAuthorizationGrantType())
                && context.getAuthorizationGrant() != null
                && context.getAuthorizationGrant().getPrincipal() instanceof OAuth2ClientAuthenticationToken client) {
            return ClientAuthenticationMethod.NONE.equals(client.getClientAuthenticationMethod());
        }
        return false;
    }
}
