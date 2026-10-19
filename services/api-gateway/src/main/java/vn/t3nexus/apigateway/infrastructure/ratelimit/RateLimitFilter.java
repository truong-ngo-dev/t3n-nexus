package vn.t3nexus.apigateway.infrastructure.ratelimit;

import lombok.RequiredArgsConstructor;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import vn.t3nexus.apigateway.infrastructure.configuration.RouteConfiguration;
import vn.t3nexus.lib.ratelimiter.reactive.RateLimitDecision;
import vn.t3nexus.lib.ratelimiter.reactive.ReactiveRateLimiter;
import vn.t3nexus.lib.ratelimiter.reactive.TooManyRequestsResponse;

import java.util.Optional;

/**
 * Giới hạn tần suất bị động ở biên (nhóm 1, `rate-limiting-layers.md`) theo địa chỉ mạng của kết nối, hai tầng, chạy sau khi đã khớp một
 * tuyến (host lạ bị 404 trước nên không đếm gì): <b>luật theo đường dẫn trước, mức chung sau</b> (`tech.md` T1).
 * <ul>
 *   <li>Mỗi tầng chỉ đếm yêu cầu mà chính nó cho qua; yêu cầu bị chặn ở một tầng dừng ở đó và không làm tăng bộ đếm tầng sau (thư viện
 *       đã không ghi lượt khi từ chối). Kẽ hở chấp nhận: yêu cầu luật cho qua nhưng mức chung từ chối đã tốn một lượt của luật.</li>
 *   <li>Tuyến tới máy chủ giao diện không bị đếm: tài nguyên tĩnh (máy chủ phát triển phục vụ hàng trăm tệp mỗi lần mở trang) không phải
 *       đối tượng bảo vệ, và nếu đếm thì làm cạn luôn mức dành cho lời gọi API cùng địa chỉ.</li>
 *   <li>Bị chặn thì trả 429 với khung lỗi chung và {@code Retry-After} đúng thời gian còn lại (T2). Redis lỗi thì mở cửa (T3, thư viện).</li>
 * </ul>
 * Địa chỉ là địa chỉ kết nối, không đọc {@code X-Forwarded-For} (xem {@link ClientAddress}).
 */
@Component
@RequiredArgsConstructor
public class RateLimitFilter implements GlobalFilter, Ordered {

    private static final String RULE_KEY_PREFIX = "ratelimit:rule:";
    private static final String IP_KEY_PREFIX   = "ratelimit:ip:";

    private final ReactiveRateLimiter rateLimiter;
    private final RateLimitPolicy policy;

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        if (route != null && RouteConfiguration.FRONTEND_ROUTE_ID.equals(route.getId())) {
            return chain.filter(exchange);
        }

        String address = ClientAddress.of(exchange);
        var request = exchange.getRequest();
        Optional<PathRateLimitRule> rule = policy.ruleFor(request.getHeaders().getFirst("Host"),
                request.getMethod().name(), request.getPath().value());

        Mono<RateLimitDecision> ruleDecision = rule
                .map(r -> rateLimiter.acquire(RULE_KEY_PREFIX + r.name() + ":" + address, r.limit(), r.window()))
                .orElseGet(() -> Mono.just(RateLimitDecision.allow()));

        return ruleDecision.flatMap(decision -> {
            if (!decision.allowed()) return TooManyRequestsResponse.write(exchange, decision);
            return rateLimiter.acquire(IP_KEY_PREFIX + address, policy.defaultLimit(), policy.defaultWindow())
                    .flatMap(general -> general.allowed() ? chain.filter(exchange) : TooManyRequestsResponse.write(exchange, general));
        });
    }
}
