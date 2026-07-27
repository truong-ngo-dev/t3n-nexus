package vn.t3nexus.lib.ratelimiter.reactive;

import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Non-blocking counterpart of {@code vn.t3nexus.lib.ratelimiter.RateLimiter} (rate-limiter-starter) —
 * same sliding-window-log semantics, executed through a reactive Redis client so callers on the
 * Netty event loop (web-gateway, api-gateway) never block.
 */
public interface ReactiveRateLimiter {

    /**
     * @return true if acquired (request allowed), false if the limit for this key/window is exceeded
     */
    Mono<Boolean> tryAcquire(String key, int limit, Duration window);
}
