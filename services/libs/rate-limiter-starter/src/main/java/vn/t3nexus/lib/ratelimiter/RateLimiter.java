package vn.t3nexus.lib.ratelimiter;

import java.time.Duration;

/**
 * Sliding window rate limiter backed by Redis.
 *
 * Two usage patterns:
 *
 * Per-key / fail-fast (API abuse prevention):
 *   if (!rateLimiter.tryAcquire("resend:" + email, 3, Duration.ofHours(1)))
 *       throw RateLimitException.tooManyRequests();
 *
 * Global throughput / blocking (external quota enforcement):
 *   while (!rateLimiter.tryAcquire("email:bulk", 14, Duration.ofSeconds(1)))
 *       Thread.sleep(50);
 */
public interface RateLimiter {

    /**
     * Attempts to record one request within the sliding window.
     *
     * @return true if allowed (under limit), false if rate limit exceeded
     */
    boolean tryAcquire(String key, int limit, Duration window);
}
