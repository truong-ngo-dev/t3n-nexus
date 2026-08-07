package vn.t3nexus.lib.ratelimiter;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.time.Duration;
import java.util.List;

@RequiredArgsConstructor
public class RedisRateLimiter implements RateLimiter {

    /**
     * <p>Sliding window rate limiter implemented via Redis sorted set (ZSET).</p>
     *
     * <p><b>Steps (atomic &mdash; Lua runs single-threaded in Redis):</b></p>
     * <ol>
     *   <li><code>ZREMRANGEBYSCORE</code> &mdash; evict entries outside the current window</li>
     *   <li><code>ZCARD</code> &mdash; count remaining entries</li>
     *   <li>If <code>count &lt; limit</code>: <code>ZADD</code> + <code>PEXPIRE</code> &rarr; allow</li>
     *   <li>Otherwise &rarr; deny</li>
     * </ol>

     * <p>Member format <code>"timestamp-rank"</code> ensures uniqueness even when two requests
     * arrive within the same millisecond (<code>ZADD</code> deduplicates on member, not score).</p>
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
