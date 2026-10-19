package vn.t3nexus.storefrontwebbff.infrastructure.ratelimit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.NullMarked;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import vn.t3nexus.lib.ratelimiter.reactive.ReactiveRateLimiter;
import vn.t3nexus.lib.ratelimiter.reactive.TooManyRequestsResponse;

import java.time.Duration;
import java.util.Optional;

/**
 * Generic per-user safety net (technical, not business rule) — protects against a broken/
 * runaway client hammering any /api/** endpoint. Endpoint-specific business throttles (OTP,
 * login) stay explicit in each service's application layer, independent of this filter.
 * <p>
 * Applies only to authenticated requests — anonymous/guest traffic is covered by the
 * per-IP layer at api-gateway, not here (storefront-web-bff has no identity for unauthenticated calls).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserRateLimitFilter implements GlobalFilter, Ordered {

    private static final String PROTECTED_PATH_PREFIX = "/api/";
    private static final String KEY_PREFIX             = "ratelimit:user:";

    private final ReactiveRateLimiter rateLimiter;

    @Value("${app.ratelimit.default.limit:300}")
    private int limit;

    @Value("${app.ratelimit.default.window-seconds:60}")
    private long windowSeconds;

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }

    @Override
    @NullMarked
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!exchange.getRequest().getPath().value().startsWith(PROTECTED_PATH_PREFIX)) {
            return chain.filter(exchange);
        }

        // Mono<Void> luôn rỗng khi xong nên không dùng switchIfEmpty trên kết quả của chain: chọn nhánh trước rồi mới chạy một lần
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .filter(Authentication::isAuthenticated)
                .map(Authentication::getName)
                .map(Optional::of)
                .defaultIfEmpty(Optional.empty())
                .flatMap(user -> user
                        .map(s -> checkLimit(s, exchange, chain))
                        .orElseGet(() -> chain.filter(exchange)));
    }

    private Mono<Void> checkLimit(String user, ServerWebExchange exchange, GatewayFilterChain chain) {
        return rateLimiter.acquire(KEY_PREFIX + user, limit, Duration.ofSeconds(windowSeconds))
                .flatMap(decision -> decision.allowed() ? chain.filter(exchange) : TooManyRequestsResponse.write(exchange, decision));
    }
}
