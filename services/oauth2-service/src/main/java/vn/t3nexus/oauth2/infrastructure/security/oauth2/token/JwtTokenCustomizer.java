package vn.t3nexus.oauth2.infrastructure.security.oauth2.token;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.endpoint.OidcParameterNames;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.stereotype.Component;
import vn.t3nexus.oauth2.domain.login_session.SessionLifetimePolicy;
import vn.t3nexus.oauth2.infrastructure.security.oauth2.key.RsaKeyPairRepository;

import java.time.Clock;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Bộ tùy biến JWT duy nhất (ADR-0003 mục 3, 4):
 * <ul>
 *   <li>Header {@code kid}: khóa dùng để ký (sẽ chuyển sang bản chụp trong bộ nhớ ở `tech.md` T4).</li>
 *   <li>Access token và ID token của một phiên đăng nhập: claim {@code sid} (mã phiên ứng dụng của ta) lấy từ thuộc tính của
 *       bản ghi ủy quyền ({@link SessionBinding}); access token thêm claim {@code role}. {@code exp} cắt theo hạn phiên:
 *       nhỏ hơn của (bây giờ + TTL access, hạn phiên). ID token dùng cùng hạn với access token (`tech.md` T5).</li>
 *   <li>Phiên đã hết hạn lúc cấp thì từ chối bằng {@code invalid_grant}, không cấp token đã quá hạn.</li>
 *   <li>Không còn claim {@code oss_id}, {@code roles}, {@code email}: hợp đồng danh tính chỉ có mã tài khoản ({@code sub}),
 *       vai, {@code sid}, hạn. Không bao giờ có mã cookie của Spring hay {@code authorization_id}.</li>
 *   <li>Token không thuộc phiên người dùng (client credentials giữa các dịch vụ) chỉ được thêm {@code kid}.</li>
 *   <li>ID token: giữ việc chép claim của bên thứ ba từ {@link OidcUser} (đăng nhập mạng xã hội, ngoài phạm vi C0).</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtTokenCustomizer implements OAuth2TokenCustomizer<JwtEncodingContext> {

    public static final String CLAIM_SID  = "sid";
    public static final String CLAIM_ROLE = "role";

    private static final Set<String> ID_TOKEN_CLAIMS = Set.of(
            IdTokenClaimNames.ISS, IdTokenClaimNames.SUB, IdTokenClaimNames.AUD,
            IdTokenClaimNames.EXP, IdTokenClaimNames.IAT, IdTokenClaimNames.AUTH_TIME,
            IdTokenClaimNames.NONCE, IdTokenClaimNames.ACR, IdTokenClaimNames.AMR,
            IdTokenClaimNames.AZP, IdTokenClaimNames.AT_HASH, IdTokenClaimNames.C_HASH);

    private final RsaKeyPairRepository keyPairRepository;
    private final SessionLifetimePolicy lifetimePolicy;
    private final Clock clock;

    @Override
    public void customize(JwtEncodingContext context) {
        // kid header — luôn set để bên nhận biết dùng khóa nào để kiểm chữ ký
        String kid = keyPairRepository.findKeyPairs().getFirst().id();
        context.getJwsHeader().keyId(kid);

        Optional<SessionBinding> binding = SessionBinding.from(context.getAuthorization());

        if (OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType())) {
            binding.ifPresent(b -> {
                applySession(context, b);
                context.getClaims().claim(CLAIM_ROLE, b.role().name());
            });
        }

        if (OidcParameterNames.ID_TOKEN.equals(context.getTokenType().getValue())) {
            binding.ifPresent(b -> applySession(context, b));
            // Copy third-party claims từ OidcUser vào ID token
            Map<String, Object> thirdPartyClaims = extractClaims(context.getPrincipal());
            context.getClaims().claims(existingClaims -> {
                existingClaims.keySet().forEach(thirdPartyClaims::remove);
                ID_TOKEN_CLAIMS.forEach(thirdPartyClaims::remove);
                existingClaims.putAll(thirdPartyClaims);
            });
        }
    }

    /** Đặt {@code sid} và cắt {@code exp} theo hạn phiên; phiên đã hết hạn thì không cấp token. */
    private void applySession(JwtEncodingContext context, SessionBinding binding) {
        Instant now = clock.instant();
        if (!binding.sessionExpiresAt().isAfter(now)) {
            throw new OAuth2AuthenticationException(new OAuth2Error(OAuth2ErrorCodes.INVALID_GRANT,
                    "The login session has expired", null));
        }
        context.getClaims().claim(CLAIM_SID, binding.sid());
        context.getClaims().expiresAt(lifetimePolicy.accessTokenExpiresAt(binding.role(), now, binding.sessionExpiresAt()));
    }

    private Map<String, Object> extractClaims(Authentication principal) {
        if (principal.getPrincipal() instanceof OidcUser oidcUser) {
            OidcIdToken idToken = oidcUser.getIdToken();
            return new HashMap<>(idToken.getClaims());
        } else if (principal.getPrincipal() instanceof OAuth2User oauth2User) {
            return new HashMap<>(oauth2User.getAttributes());
        }
        return Collections.emptyMap();
    }
}
