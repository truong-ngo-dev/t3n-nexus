package vn.t3nexus.lib.ratelimiter.reactive;

import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Non-blocking counterpart of {@code vn.t3nexus.lib.ratelimiter.RateLimiter} (rate-limiter-starter) —
 * same sliding-window-log semantics, executed through a reactive Redis client so callers on the
 * Netty event loop (storefront-web-bff, api-gateway) never block.
 */
public interface ReactiveRateLimiter {

    /**
     * Xin một lượt. Chỉ yêu cầu được cho qua mới ghi lượt vào bộ đếm; bị từ chối thì không ghi gì. Khi từ chối, kết quả mang thời gian
     * còn lại đến khi giải phóng một lượt.
     */
    Mono<RateLimitDecision> acquire(String key, int limit, Duration window);

    /**
     * @return true if acquired (request allowed), false if the limit for this key/window is exceeded
     */
    default Mono<Boolean> tryAcquire(String key, int limit, Duration window) {
        return acquire(key, limit, window).map(RateLimitDecision::allowed);
    }
}
