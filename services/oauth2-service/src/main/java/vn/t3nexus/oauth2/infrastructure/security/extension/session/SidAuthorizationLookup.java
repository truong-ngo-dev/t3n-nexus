package vn.t3nexus.oauth2.infrastructure.security.extension.session;

import com.nimbusds.jwt.SignedJWT;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.endpoint.OidcParameterNames;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import vn.t3nexus.oauth2.domain.login_session.ClientSessionId;
import vn.t3nexus.oauth2.domain.login_session.LoginSessionRepository;
import vn.t3nexus.oauth2.infrastructure.security.oauth2.token.JwtTokenCustomizer;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.ParseException;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * Tra bản ghi ủy quyền theo token <b>mà không quét theo giá trị token</b> (ADR-0003 mục 8): cột token là chuỗi dài, không có chỉ
 * mục, nên mỗi kiểu tra cứu đi bằng một khóa chính, qua mã phiên ứng dụng {@code sid} ({@code client_sessions.id}) rồi
 * {@code authorization_id}. Đây là phần {@code findByToken} của {@link SessionBoundAuthorizationService}, tách ra để lớp bọc chỉ còn lo
 * việc gắn phiên khi {@code save}.
 * <p>
 * <table>
 *   <caption>Cách tra theo loại token Spring hỏi</caption>
 *   <tr><td>Mã ủy quyền ({@code code})</td><td>chuyển cho lớp bên dưới, có chỉ mục riêng (V14)</td></tr>
 *   <tr><td>Refresh token (chuỗi mờ)</td><td>{@code sid} từ tham số của yêu cầu ({@link RequestedSidSource}); so giá trị token</td></tr>
 *   <tr><td>Access token (JWT)</td><td>{@code sid} từ claim; so giá trị token đã lưu</td></tr>
 *   <tr><td>ID token ({@code id_token_hint})</td><td>kiểm chữ ký và issuer ({@link IdTokenHintVerifier}, không kiểm hạn), {@code sid}
 *       từ claim; <b>không</b> so giá trị với ID token đã lưu vì làm mới token đổi ID token trong bản ghi còn bên gọi giữ ID token
 *       lúc đăng nhập; chữ ký và {@code sub} đã gắn hint với đúng phiên</td></tr>
 *   <tr><td>Loại {@code null} (introspect, revoke)</td><td>theo hình dạng: JWT thì {@code sid} từ claim và so với access token hoặc ID
 *       token đã lưu; chuỗi mờ thì như refresh token; còn lại không thấy</td></tr>
 *   <tr><td>Kiểu khác ({@code state}, mã thiết bị, ...)</td><td>không dùng ở dịch vụ này: không thấy, không quét</td></tr>
 * </table>
 *
 * <p>{@code sid} sai, bản ghi không thuộc {@code sid} đó, hoặc giá trị token khác đều cho "không thấy"; so giá trị theo thời gian hằng.
 */
public class SidAuthorizationLookup {

    private static final OAuth2TokenType CODE     = new OAuth2TokenType(OAuth2ParameterNames.CODE);
    private static final String          ID_TOKEN = OidcParameterNames.ID_TOKEN;

    private final OAuth2AuthorizationService delegate;
    private final LoginSessionRepository     loginSessionRepository;
    private final RequestedSidSource         requestedSid;
    private final IdTokenHintVerifier        hintVerifier;

    public SidAuthorizationLookup(OAuth2AuthorizationService delegate, LoginSessionRepository loginSessionRepository,
                                  RequestedSidSource requestedSid, IdTokenHintVerifier hintVerifier) {
        this.delegate               = delegate;
        this.loginSessionRepository = loginSessionRepository;
        this.requestedSid           = requestedSid;
        this.hintVerifier           = hintVerifier;
    }

    public OAuth2Authorization find(String token, OAuth2TokenType tokenType) {
        if (token == null || token.isBlank()) return null;
        if (tokenType == null) return findByShape(token);
        if (CODE.equals(tokenType)) return delegate.findByToken(token, tokenType);
        if (OAuth2TokenType.REFRESH_TOKEN.equals(tokenType)) return findRefreshToken(token);
        if (OAuth2TokenType.ACCESS_TOKEN.equals(tokenType)) return findAccessToken(token);
        if (ID_TOKEN.equals(tokenType.getValue())) return findIdTokenHint(token);
        return null;
    }

    // ───────────── từng loại ─────────────

    private OAuth2Authorization findRefreshToken(String token) {
        return requestedSid.current()
                .flatMap(sid -> findBySid(sid, token, SidAuthorizationLookup::refreshTokenValue))
                .orElse(null);
    }

    private OAuth2Authorization findAccessToken(String token) {
        return sidClaimOf(token)
                .flatMap(sid -> findBySid(sid, token, SidAuthorizationLookup::accessTokenValue))
                .orElse(null);
    }

    /** Hint do chính ta ký: chữ ký và issuer đúng, {@code sid} tra ra bản ghi, {@code sub} khớp chủ bản ghi, bản ghi có ID token. */
    private OAuth2Authorization findIdTokenHint(String hint) {
        Optional<Jwt> verified = hintVerifier.verify(hint);
        if (verified.isEmpty()) return null;
        Jwt jwt = verified.get();
        String sid = jwt.getClaimAsString(JwtTokenCustomizer.CLAIM_SID);
        if (sid == null) return null;
        OAuth2Authorization authorization = findBySid(sid, null, null).orElse(null);
        if (authorization == null) return null;
        if (authorization.getToken(OidcIdToken.class) == null) return null;
        return java.util.Objects.equals(jwt.getSubject(), authorization.getPrincipalName()) ? authorization : null;
    }

    /** Loại không biết: dựa vào hình dạng token để vẫn tra bằng khóa chính. */
    private OAuth2Authorization findByShape(String token) {
        Optional<String> sid = sidClaimOf(token);
        if (sid.isPresent()) {
            return findBySid(sid.get(), token, a -> sameValue(accessTokenValue(a), token) ? token : idTokenValue(a)).orElse(null);
        }
        return findRefreshToken(token);
    }

    // ───────────── dùng chung ─────────────

    /**
     * Bản ghi hiện hành của phiên ứng dụng {@code sid} (một truy vấn theo khóa chính rồi một lần đọc bản ghi). {@code storedValue} null
     * thì không so giá trị token.
     */
    private Optional<OAuth2Authorization> findBySid(String sid, String token, Function<OAuth2Authorization, String> storedValue) {
        ClientSessionId clientSessionId;
        try {
            clientSessionId = ClientSessionId.of(UUID.fromString(sid));
        } catch (IllegalArgumentException malformed) {
            return Optional.empty();
        }
        return loginSessionRepository.findAuthorizationIdByClientSessionId(clientSessionId)
                .map(delegate::findById)
                .filter(authorization -> storedValue == null || sameValue(storedValue.apply(authorization), token));
    }

    private static String refreshTokenValue(OAuth2Authorization authorization) {
        return authorization.getRefreshToken() == null ? null : authorization.getRefreshToken().getToken().getTokenValue();
    }

    private static String accessTokenValue(OAuth2Authorization authorization) {
        OAuth2Authorization.Token<OAuth2AccessToken> token = authorization.getToken(OAuth2AccessToken.class);
        return token == null ? null : token.getToken().getTokenValue();
    }

    private static String idTokenValue(OAuth2Authorization authorization) {
        OAuth2Authorization.Token<OidcIdToken> token = authorization.getToken(OidcIdToken.class);
        return token == null ? null : token.getToken().getTokenValue();
    }

    /** Claim {@code sid} của một JWT (chỉ đọc, không kiểm chữ ký); rỗng nếu không phải JWT hoặc không có claim. */
    private static Optional<String> sidClaimOf(String jwt) {
        try {
            return Optional.ofNullable(SignedJWT.parse(jwt).getJWTClaimsSet().getStringClaim(JwtTokenCustomizer.CLAIM_SID));
        } catch (ParseException notAJwt) {
            return Optional.empty();
        }
    }

    private static boolean sameValue(String stored, String presented) {
        if (stored == null || presented == null) return false;
        return MessageDigest.isEqual(stored.getBytes(StandardCharsets.UTF_8), presented.getBytes(StandardCharsets.UTF_8));
    }
}
