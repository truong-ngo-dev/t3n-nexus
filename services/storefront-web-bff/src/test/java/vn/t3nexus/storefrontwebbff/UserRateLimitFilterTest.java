package vn.t3nexus.storefrontwebbff;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import reactor.core.publisher.Mono;
import vn.t3nexus.lib.ratelimiter.reactive.RateLimitDecision;
import vn.t3nexus.lib.ratelimiter.reactive.ReactiveRateLimiter;
import vn.t3nexus.storefrontwebbff.infrastructure.ratelimit.UserRateLimitFilter;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.util.ReflectionTestUtils.setField;

/**
 * Giới hạn theo người dùng ở BFF (feature 06, nhóm A, R5): chỉ áp cho {@code /api/**} của yêu cầu đã xác thực, đếm theo tên người dùng,
 * bị chặn thì trả 429 với khung lỗi chung và {@code Retry-After} đúng thời gian còn lại; yêu cầu được qua chỉ chạy chuỗi lọc đúng một lần.
 */
class UserRateLimitFilterTest {

    private final List<String> keys = new ArrayList<>();
    private final AtomicInteger chainCalls = new AtomicInteger();
    private RateLimitDecision decision = RateLimitDecision.allow();

    private final ReactiveRateLimiter limiter = (key, limit, window) -> {
        keys.add(key);
        return Mono.just(decision);
    };
    private final GatewayFilterChain chain = exchange -> Mono.fromRunnable(chainCalls::incrementAndGet);

    private UserRateLimitFilter filter() {
        UserRateLimitFilter filter = new UserRateLimitFilter(limiter);
        setField(filter, "limit", 5);
        setField(filter, "windowSeconds", 60L);
        return filter;
    }

    private MockServerWebExchange exchange(String path) {
        return MockServerWebExchange.from(MockServerHttpRequest.get(path));
    }

    private Mono<Void> asUser(UserRateLimitFilter filter, MockServerWebExchange exchange) {
        var auth = UsernamePasswordAuthenticationToken.authenticated("alice", "n/a", List.of());
        return filter.filter(exchange, chain)
                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(new SecurityContextImpl(auth))));
    }

    @Test
    void authenticated_api_request_within_the_limit_runs_the_chain_exactly_once() {
        asUser(filter(), exchange("/api/orders")).block();

        assertThat(keys).containsExactly("ratelimit:user:alice");
        assertThat(chainCalls).hasValue(1);
    }

    @Test
    void denied_request_gets_429_with_remaining_retry_after_and_never_reaches_the_chain() {
        decision = RateLimitDecision.deny(Duration.ofSeconds(17));
        var exchange = exchange("/api/orders");

        asUser(filter(), exchange).block();

        assertThat(exchange.getResponse().getStatusCode().value()).isEqualTo(429);
        assertThat(exchange.getResponse().getHeaders().getFirst("Retry-After")).isEqualTo("17");
        assertThat(chainCalls).hasValue(0);
    }

    @Test
    void anonymous_and_non_api_requests_are_not_counted_and_pass_once() {
        filter().filter(exchange("/api/orders"), chain).block();                    // chưa xác thực
        asUser(filter(), exchange("/bff/auth/session")).block();                    // ngoài /api/

        assertThat(keys).isEmpty();
        assertThat(chainCalls).hasValue(2);
    }
}
