package vn.t3nexus.webgateway.infrastructure.ratelimit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import vn.t3nexus.lib.ratelimiter.reactive.ReactiveRateLimiter;

import java.time.Duration;

/**
 * Generic per-user safety net (technical, not business rule) — protects against a broken/
 * runaway client hammering any /api/** endpoint. Endpoint-specific business throttles (OTP,
 * login) stay explicit in each service's application layer, independent of this filter.
 * <p>
 * Applies only to authenticated requests — anonymous/guest traffic is covered by the
 * per-IP layer at api-gateway, not here (web-gateway has no identity for unauthenticated calls).
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
    @SuppressWarnings("all")
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!exchange.getRequest().getPath().value().startsWith(PROTECTED_PATH_PREFIX)) {
            return chain.filter(exchange);
        }

        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .flatMap(authentication -> checkLimit(authentication, exchange, chain))
                .switchIfEmpty(chain.filter(exchange));
    }

    private Mono<Void> checkLimit(Authentication authentication, ServerWebExchange exchange, GatewayFilterChain chain) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return chain.filter(exchange);
        }

        String   key    = KEY_PREFIX + authentication.getName();
        Duration window = Duration.ofSeconds(windowSeconds);

        return rateLimiter.tryAcquire(key, limit, window)
                .flatMap(allowed -> allowed ? chain.filter(exchange) : reject(exchange));
    }

    private Mono<Void> reject(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        exchange.getResponse().getHeaders().add("Retry-After", String.valueOf(windowSeconds));
        return exchange.getResponse().setComplete();
    }
}
