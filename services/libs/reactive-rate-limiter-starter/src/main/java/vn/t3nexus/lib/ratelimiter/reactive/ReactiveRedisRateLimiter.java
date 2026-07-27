package vn.t3nexus.lib.ratelimiter.reactive;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;

/**
 * Sliding window log via Redis sorted set — same algorithm/script as the blocking
 * RedisRateLimiter in rate-limiter-starter, executed through the reactive Redis client
 * so it never blocks the Netty event loop.
 */
@Slf4j
@RequiredArgsConstructor
public class ReactiveRedisRateLimiter implements ReactiveRateLimiter {

    private static final DefaultRedisScript<Long> SCRIPT = new DefaultRedisScript<>("""
            local key          = KEYS[1]
            local now          = tonumber(ARGV[1])
            local window_start = tonumber(ARGV[2])
            local limit        = tonumber(ARGV[3])
            local ttl_ms       = tonumber(ARGV[4])
            redis.call('ZREMRANGEBYSCORE', key, '-inf', window_start)
            local count = redis.call('ZCARD', key)
            if count < limit then
                redis.call('ZADD', key, now, tostring(now) .. '-' .. tostring(count + 1))
                redis.call('PEXPIRE', key, ttl_ms)
                return 1
            end
            return 0
            """, Long.class);

    private final ReactiveStringRedisTemplate redisTemplate;

    @Override
    public Mono<Boolean> tryAcquire(String key, int limit, Duration window) {
        long now         = System.currentTimeMillis();
        long windowStart = now - window.toMillis();

        return redisTemplate.execute(
                        SCRIPT,
                        List.of(key),
                        List.of(String.valueOf(now), String.valueOf(windowStart),
                                String.valueOf(limit), String.valueOf(window.toMillis())))
                .next()
                .map(result -> Long.valueOf(1L).equals(result))
                .defaultIfEmpty(true)
                .onErrorResume(ex -> {
                    log.warn("[ReactiveRedisRateLimiter] Redis unavailable, failing open for key={}: {}", key, ex.getMessage());
                    return Mono.just(true);
                });
    }
}
