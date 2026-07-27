package vn.t3nexus.apigateway.infrastructure.ratelimit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import vn.t3nexus.lib.ratelimiter.reactive.ReactiveRateLimiter;

import java.net.InetSocketAddress;
import java.time.Duration;

/**
 * Coarse edge safety net theo IP — áp dụng cho MỌI request (kể cả /auth/**, chưa có identity),
 * chạy trước khi request được route xuống web-gateway/mobile-gateway/oauth2-service.
 * <p>
 * Không đọc X-Forwarded-For — theo 8. deployment.md, api-gateway là hop ngoài cùng (single EC2 +
 * Docker Compose, không có LB/CDN phía trước), tin header đó cho phép client tự spoof IP để bypass.
 * Chỉ đổi sang đọc X-Forwarded-For nếu sau này có ALB/CloudFront đứng trước api-gateway.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IpRateLimitFilter implements GlobalFilter, Ordered {

    private static final String KEY_PREFIX = "ratelimit:ip:";

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
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String   key    = KEY_PREFIX + resolveClientIp(exchange);
        Duration window = Duration.ofSeconds(windowSeconds);

        return rateLimiter.tryAcquire(key, limit, window)
                .flatMap(allowed -> allowed ? chain.filter(exchange) : reject(exchange));
    }

    private String resolveClientIp(ServerWebExchange exchange) {
        InetSocketAddress remoteAddress = exchange.getRequest().getRemoteAddress();
        return (remoteAddress != null && remoteAddress.getAddress() != null)
                ? remoteAddress.getAddress().getHostAddress()
                : "unknown";
    }

    private Mono<Void> reject(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        exchange.getResponse().getHeaders().add("Retry-After", String.valueOf(windowSeconds));
        return exchange.getResponse().setComplete();
    }
}
