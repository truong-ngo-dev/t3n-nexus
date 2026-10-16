package vn.t3nexus.oauth2.infrastructure.security.extension.session;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationCode;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.transaction.support.TransactionOperations;
import vn.t3nexus.oauth2.application.login_session.record_client_session_issued.RecordClientSessionIssued;
import vn.t3nexus.oauth2.application.login_session.resolve_authorization_session.ResolveAuthorizationSession;
import vn.t3nexus.oauth2.domain.login_session.ClientSessionId;
import vn.t3nexus.oauth2.domain.login_session.LoginSessionException;
import vn.t3nexus.oauth2.domain.login_session.LoginSessionRepository;

import com.nimbusds.jwt.SignedJWT;
import vn.t3nexus.oauth2.infrastructure.security.oauth2.token.JwtTokenCustomizer;
import vn.t3nexus.oauth2.infrastructure.security.oauth2.token.SessionBinding;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.ParseException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * Lớp bọc {@link OAuth2AuthorizationService} nối bản ghi ủy quyền với phiên đăng nhập của ta (ADR-0003 mục 3, 9). Thay cho
 * lớp bọc của mô hình phiên một lớp cũ. Hai điểm móc, theo vòng đời mà Spring gọi {@code save()}:
 *
 * <ol>
 *   <li><b>Mã ủy quyền vừa được cấp</b> (có mã, chưa có access token, chưa gắn phiên): gắn {@link SessionBinding} (mã phiên
 *       người dùng, {@code sid} đã chốt, hạn phiên, vai) lấy từ cửa kiểm hạn của yêu cầu này. Không có kết quả của cửa kiểm hạn
 *       (chưa có phiên đăng nhập hợp lệ) thì từ chối bằng {@code login_required}. {@code sid} phải chốt từ đây vì JWT được ký
 *       trước điểm móc thứ hai.</li>
 *   <li><b>Token vừa được cấp</b> (có access token chưa bị vô hiệu, đã gắn phiên): <b>trong một giao dịch</b>, lưu bản ghi ủy
 *       quyền, ghi nhận vào phiên ({@link RecordClientSessionIssued}: cấp phiên ứng dụng hoặc làm mới, đối chiếu {@code sid},
 *       phát event) và xóa bản ghi ủy quyền cũ nếu xác thực im lặng đổi liên kết. Lỗi ở bất kỳ bước nào quay lui cả giao dịch,
 *       nên không có token được lưu mà phiên không ghi nhận.</li>
 * </ol>
 *
 * <p>Bản ghi không thuộc phiên người dùng (client credentials giữa các dịch vụ) đi thẳng qua. Lỗi miền của phiên được đổi thành
 * {@code invalid_grant} để điểm cuối token trả đúng mã. Tra cứu và xóa chuyển nguyên cho lớp bọc bên dưới; tra theo {@code sid}
 * khi làm mới thêm ở bước 9a.6.
 */
@Slf4j
public class LoginSessionAuthorizationService implements OAuth2AuthorizationService {

    private final OAuth2AuthorizationService delegate;
    private final RecordClientSessionIssued  recordClientSessionIssued;
    private final AuthorizationSessionSource sessionSource;
    private final TransactionOperations      transactions;
    private final LoginSessionRepository     loginSessionRepository;
    private final RequestedSidSource requestedSid;
    private final List<IssuedTokenListener> listeners;

    public LoginSessionAuthorizationService(OAuth2AuthorizationService delegate,
                                            RecordClientSessionIssued recordClientSessionIssued,
                                            AuthorizationSessionSource sessionSource,
                                            TransactionOperations transactions,
                                            LoginSessionRepository loginSessionRepository,
                                            RequestedSidSource requestedSid) {
        this(delegate, recordClientSessionIssued, sessionSource, transactions, loginSessionRepository, requestedSid, List.of());
    }

    /** @param listeners bên nhận biết một lần cấp token đã hoàn tất (chạy sau khi giao dịch xong; lỗi của chúng bị bỏ qua) */
    public LoginSessionAuthorizationService(OAuth2AuthorizationService delegate,
                                            RecordClientSessionIssued recordClientSessionIssued,
                                            AuthorizationSessionSource sessionSource,
                                            TransactionOperations transactions,
                                            LoginSessionRepository loginSessionRepository,
                                            RequestedSidSource requestedSid,
                                            List<IssuedTokenListener> listeners) {
        this.delegate                  = delegate;
        this.recordClientSessionIssued = recordClientSessionIssued;
        this.sessionSource             = sessionSource;
        this.transactions              = transactions;
        this.loginSessionRepository    = loginSessionRepository;
        this.requestedSid              = requestedSid;
        this.listeners                 = List.copyOf(listeners);
    }

    @Override
    public void save(OAuth2Authorization authorization) {
        if (isAuthorizationCodeIssued(authorization)) {
            delegate.save(bindToLoginSession(authorization));
            return;
        }
        if (isTokenIssuedForSession(authorization)) {
            transactions.executeWithoutResult(status -> {
                delegate.save(authorization);
                recordInSession(authorization);
            });
            notifyListeners(authorization);
            return;
        }
        delegate.save(authorization);
    }

    /** Báo cho các bên quan sát sau khi giao dịch đã xong; lỗi của họ không được làm hỏng việc cấp token. */
    private void notifyListeners(OAuth2Authorization authorization) {
        for (IssuedTokenListener listener : listeners) {
            try {
                listener.onTokenIssued(authorization);
            } catch (RuntimeException e) {
                log.warn("issued-token listener failed: {}", e.getClass().getSimpleName());
            }
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

    /**
     * Tra bản ghi ủy quyền theo token mà <b>không quét theo giá trị token</b> (ADR-0003 mục 8): cột token là chuỗi dài, không
     * có chỉ mục, nên mỗi kiểu tra cứu đi bằng một khóa chính.
     * <ul>
     *   <li><b>Refresh token</b> (chuỗi mờ): tra theo {@code sid} BFF gửi kèm yêu cầu (tham số {@code sid}); thiếu {@code sid}
     *       thì không tìm thấy.</li>
     *   <li><b>Access token</b> (JWT, dùng ở userinfo): lấy {@code sid} từ chính claim của token. Phần giải mã không xác thực
     *       gì; việc xác thực là bước <b>so giá trị token</b> với giá trị đã lưu của bản ghi tìm được.</li>
     *   <li><b>Mã ủy quyền</b> ({@code code}, 16 ký tự, sống 5 phút): chuyển cho lớp bọc bên dưới, có chỉ mục riêng (V14).</li>
     *   <li>Mọi kiểu khác (ID token, {@code state}, mã thiết bị, kiểu không xác định): không tìm thấy, không quét.</li>
     * </ul>
     * Với hai kiểu đầu: {@code sid} sai, bản ghi không phải của {@code sid} đó, hoặc giá trị token khác đều cho không tìm thấy;
     * so sánh giá trị theo thời gian hằng.
     */
    @Override
    public OAuth2Authorization findByToken(String token, OAuth2TokenType tokenType) {
        if (token == null || tokenType == null) return null;
        if (OAuth2ParameterNames.CODE.equals(tokenType.getValue())) {
            return delegate.findByToken(token, tokenType);
        }
        if (OAuth2TokenType.REFRESH_TOKEN.equals(tokenType)) {
            return requestedSid.current()
                    .flatMap(sid -> findBySid(sid, token, LoginSessionAuthorizationService::refreshTokenValue))
                    .orElse(null);
        }
        if (OAuth2TokenType.ACCESS_TOKEN.equals(tokenType)) {
            return sidClaimOf(token)
                    .flatMap(sid -> findBySid(sid, token, LoginSessionAuthorizationService::accessTokenValue))
                    .orElse(null);
        }
        return null;
    }

    private Optional<OAuth2Authorization> findBySid(String sid, String token,
                                                    Function<OAuth2Authorization, String> storedValue) {
        ClientSessionId clientSessionId;
        try {
            clientSessionId = ClientSessionId.of(UUID.fromString(sid));
        } catch (IllegalArgumentException malformed) {
            return Optional.empty();
        }
        return loginSessionRepository.findAuthorizationIdByClientSessionId(clientSessionId)
                .map(delegate::findById)
                .filter(authorization -> sameValue(storedValue.apply(authorization), token));
    }

    private static String refreshTokenValue(OAuth2Authorization authorization) {
        return authorization.getRefreshToken() == null ? null : authorization.getRefreshToken().getToken().getTokenValue();
    }

    private static String accessTokenValue(OAuth2Authorization authorization) {
        return authorization.getAccessToken() == null ? null : authorization.getAccessToken().getToken().getTokenValue();
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

    // ───────────── Điểm móc 1: mã ủy quyền vừa được cấp ─────────────

    private static boolean isAuthorizationCodeIssued(OAuth2Authorization authorization) {
        return authorization.getToken(OAuth2AuthorizationCode.class) != null
                && authorization.getToken(OAuth2AccessToken.class) == null
                && SessionBinding.from(authorization).isEmpty();
    }

    private OAuth2Authorization bindToLoginSession(OAuth2Authorization authorization) {
        ResolveAuthorizationSession.Result gate = sessionSource.current().orElseThrow(() ->
                new OAuth2AuthenticationException(new OAuth2Error("login_required",
                        "No valid login session for this authorization request", null)));
        SessionBinding binding = new SessionBinding(gate.loginSessionId(), gate.sid(), gate.expiresAt(), gate.role());
        return OAuth2Authorization.from(authorization)
                .attributes(attributes -> attributes.putAll(binding.toAttributes()))
                .build();
    }

    // ───────────── Điểm móc 2: token vừa được cấp ─────────────

    private static boolean isTokenIssuedForSession(OAuth2Authorization authorization) {
        OAuth2Authorization.Token<OAuth2AccessToken> accessToken = authorization.getToken(OAuth2AccessToken.class);
        return accessToken != null && !accessToken.isInvalidated() && SessionBinding.from(authorization).isPresent();
    }

    private void recordInSession(OAuth2Authorization authorization) {
        SessionBinding binding = SessionBinding.from(authorization).orElseThrow();
        try {
            RecordClientSessionIssued.Result result = recordClientSessionIssued.handle(
                    new RecordClientSessionIssued.Command(binding.loginSessionId(), authorization.getRegisteredClientId(),
                            binding.sid(), authorization.getId(), tokenIdOf(authorization)));
            result.replacedAuthorizationId().ifPresent(this::removeReplacedAuthorization);
        } catch (LoginSessionException e) {
            log.warn("token issuance rejected by the login session: code={}", e.getErrorCode().code());
            throw new OAuth2AuthenticationException(
                    new OAuth2Error(OAuth2ErrorCodes.INVALID_GRANT, "The login session is no longer valid", null), e);
        }
    }

    private void removeReplacedAuthorization(String replacedAuthorizationId) {
        OAuth2Authorization old = delegate.findById(replacedAuthorizationId);
        if (old != null) delegate.remove(old);
    }

    /** {@code jti} của access token (JWT) làm mã event; dự phòng cho token không có claim. */
    private static String tokenIdOf(OAuth2Authorization authorization) {
        OAuth2Authorization.Token<OAuth2AccessToken> token = authorization.getToken(OAuth2AccessToken.class);
        Map<String, Object> claims = token.getClaims();
        Object jti = claims == null ? null : claims.get("jti");
        if (jti != null) return jti.toString();
        return authorization.getId() + ":" + token.getToken().getIssuedAt();
    }
}
