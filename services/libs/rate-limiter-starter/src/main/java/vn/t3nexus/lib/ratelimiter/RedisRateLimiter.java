package vn.t3nexus.lib.ratelimiter;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.time.Duration;
import java.util.List;

@RequiredArgsConstructor
public class RedisRateLimiter implements RateLimiter {

    /**
     * Sliding window via sorted set.
     *
     * Steps (atomic — Lua runs single-threaded in Redis):
     *   1. ZREMRANGEBYSCORE — evict entries outside the current window
     *   2. ZCARD            — count remaining entries
     *   3. If count < limit: ZADD + PEXPIRE → allow
     *   4. Otherwise        → deny
     *
     * Member format "timestamp-rank" ensures uniqueness even when two requests
     * arrive within the same millisecond (ZADD deduplicates on member, not score).
     */
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

    private final StringRedisTemplate redisTemplate;

    @Override
    public boolean tryAcquire(String key, int limit, Duration window) {
        long now         = System.currentTimeMillis();
        long windowStart = now - window.toMillis();

        Long result = redisTemplate.execute(
                SCRIPT,
                List.of(key),
                String.valueOf(now),
                String.valueOf(windowStart),
                String.valueOf(limit),
                String.valueOf(window.toMillis())
        );

        return Long.valueOf(1L).equals(result);
    }
}
