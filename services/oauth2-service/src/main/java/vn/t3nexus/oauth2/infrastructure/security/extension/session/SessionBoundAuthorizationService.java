package vn.t3nexus.oauth2.infrastructure.security.extension.session;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationCode;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.transaction.support.TransactionOperations;
import vn.t3nexus.lib.utils.lang.Assert;
import vn.t3nexus.oauth2.application.login_session.record_client_session_issued.RecordClientSessionIssued;
import vn.t3nexus.oauth2.application.login_session.resolve_authorization_session.ResolveAuthorizationSession;
import vn.t3nexus.oauth2.domain.login_session.LoginSessionException;

import vn.t3nexus.oauth2.infrastructure.security.oauth2.token.SessionBinding;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Lớp bọc {@link OAuth2AuthorizationService} gắn bản ghi ủy quyền với một phiên đăng nhập của ta (ADR-0003 mục 3, 8, 9): mọi bản ghi
 * thuộc một phiên ứng dụng ({@code sid}), tra theo phiên đó và ghi nhận vào phiên khi cấp token. Thay cho
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
 * {@code invalid_grant} để điểm cuối token trả đúng mã. Xóa chuyển nguyên cho lớp bọc bên dưới; tra theo token do
 * {@link SidAuthorizationLookup} đảm nhận (tra bằng khóa chính, không quét token).
 */
@Slf4j
public class SessionBoundAuthorizationService implements OAuth2AuthorizationService {

    private final OAuth2AuthorizationService delegate;
    private final RecordClientSessionIssued  recordClientSessionIssued;
    private final AuthorizationSessionSource sessionSource;
    private final TransactionOperations      transactions;
    private final SidAuthorizationLookup     lookup;
    private final List<IssuedTokenListener>  listeners;

    public SessionBoundAuthorizationService(OAuth2AuthorizationService delegate,
                                            RecordClientSessionIssued recordClientSessionIssued,
                                            AuthorizationSessionSource sessionSource,
                                            TransactionOperations transactions,
                                            SidAuthorizationLookup lookup) {
        this(delegate, recordClientSessionIssued, sessionSource, transactions, lookup, List.of());
    }

    /** @param listeners bên nhận biết một lần cấp token đã hoàn tất (chạy sau khi giao dịch xong; lỗi của chúng bị bỏ qua) */
    public SessionBoundAuthorizationService(OAuth2AuthorizationService delegate,
                                            RecordClientSessionIssued recordClientSessionIssued,
                                            AuthorizationSessionSource sessionSource,
                                            TransactionOperations transactions,
                                            SidAuthorizationLookup lookup,
                                            List<IssuedTokenListener> listeners) {
        this.delegate                  = delegate;
        this.recordClientSessionIssued = recordClientSessionIssued;
        this.sessionSource             = sessionSource;
        this.transactions              = transactions;
        this.lookup                    = lookup;
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
     * Tra bản ghi theo token, không quét cột token (ADR-0003 mục 8): xem {@link SidAuthorizationLookup} cho cách tra theo từng loại
     * token mà Spring hỏi (mã, refresh token, access token, ID token của {@code id_token_hint}, loại không biết).
     */
    @Override
    public OAuth2Authorization findByToken(String token, OAuth2TokenType tokenType) {
        return lookup.find(token, tokenType);
    }

    // ───────────── Hook point 1: authorization code just have been issued ─────────────

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

    // ───────────── Hook point 2: token just have been issued ─────────────

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
        Objects.requireNonNull(token);
        Map<String, Object> claims = token.getClaims();
        Object jti = claims == null ? null : claims.get("jti");
        if (jti != null) return jti.toString();
        return authorization.getId() + ":" + token.getToken().getIssuedAt();
    }
}
