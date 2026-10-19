package vn.t3nexus.storefrontwebbff.infrastructure.configuration.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.client.ClientAuthorizationRequiredException;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientManager;
import org.springframework.web.filter.reactive.ServerWebExchangeContextFilter;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebSession;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bọc bộ quản lý ủy quyền client (bộ mà bộ lọc {@code TokenRelay} dùng để lấy token, làm mới khi hết hạn) với hai việc của `tech.md` T5:
 * <ul>
 *   <li><b>Tuần tự hóa theo phiên (R11):</b> các yêu cầu đồng thời của cùng một phiên BFF dùng chung một lần làm mới đang chạy, vì Xác thực
 *       xoay mã làm mới nên làm mới thừa là đăng xuất oan. Một thể hiện BFF ở C0 nên bảng trong tiến trình đủ (nhiều thể hiện cần khóa Redis).</li>
 *   <li><b>Phân loại lỗi (R12):</b> {@code invalid_grant} là phiên không cứu được: xóa phiên và ánh xạ, trả 401 để giao diện đăng nhập lại
 *       (SSO im lặng nếu phiên Xác thực còn). Mọi lỗi khác (Xác thực chậm, 5xx, mất kết nối) giữ phiên và trả 503.</li>
 * </ul>
 */
@Slf4j
@RequiredArgsConstructor
public class SessionAuthorizedClientManager implements ReactiveOAuth2AuthorizedClientManager {

    private static final String INVALID_GRANT = "invalid_grant";

    private final ReactiveOAuth2AuthorizedClientManager delegate;
    private final SessionMappingStore                   mappings;

    private final Map<String, Mono<OAuth2AuthorizedClient>> inFlight = new ConcurrentHashMap<>();

    @Override
    public Mono<OAuth2AuthorizedClient> authorize(OAuth2AuthorizeRequest request) {
        // Spring Security đặt exchange vào ngữ cảnh Reactor dưới khóa lớp ServerWebExchange (ServerHttpSecurity.ServerWebExchangeReactorContextWebFilter)
        return Mono.deferContextual(context -> context.<ServerWebExchange>getOrEmpty(ServerWebExchange.class).or(() -> ServerWebExchangeContextFilter.getExchange(context))
                .map(exchange -> exchange.getSession().flatMap(session -> shared(session.getId(), request)
                        .onErrorResume(error -> translate(session, error))))
                .orElseGet(() -> delegate.authorize(request)));
    }

    /** Một lần làm mới đang chạy cho mỗi phiên; yêu cầu đến sau chờ cùng kết quả, xóa khỏi bảng khi xong. */
    private Mono<OAuth2AuthorizedClient> shared(String sessionId, OAuth2AuthorizeRequest request) {
        Mono<OAuth2AuthorizedClient>[] created = newHolder();
        created[0] = delegate.authorize(request).doFinally(done -> inFlight.remove(sessionId, created[0])).cache();
        Mono<OAuth2AuthorizedClient> existing = inFlight.putIfAbsent(sessionId, created[0]);
        return existing != null ? existing : created[0];
    }

    @SuppressWarnings("unchecked")
    private static Mono<OAuth2AuthorizedClient>[] newHolder() {
        return new Mono[1];
    }

    private Mono<OAuth2AuthorizedClient> translate(WebSession session, Throwable error) {
        if (error instanceof ResponseStatusException) return Mono.error(error);
        // invalid_grant: refresh token không còn dùng được. client_authorization_required: phiên còn nhưng mất client ủy quyền (ghi Redis lỗi,
        // phiên hỏng); cả hai đều không cứu được bằng thử lại nên xóa phiên (không chuyển yêu cầu đi không có token).
        if (error instanceof ClientAuthorizationRequiredException
                || error instanceof OAuth2AuthorizationException authorization && INVALID_GRANT.equals(authorization.getError().getErrorCode())) {
            log.info("[Refresh] session not recoverable ({}), dropping BFF session {}", error.getClass().getSimpleName(), session.getId());
            return session.invalidate()
                    .then(mappings.unlinkBySession(session.getId()))
                    .then(Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED)));
        }
        log.warn("[Refresh] temporary failure, keeping BFF session {}: {}", session.getId(), error.toString());
        return Mono.error(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE));
    }
}
