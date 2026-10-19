package vn.t3nexus.lib.ratelimiter.reactive;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.List;

/**
 * Sliding window log via Redis sorted set — same algorithm as the blocking RedisRateLimiter in rate-limiter-starter, executed through
 * the reactive Redis client so it never blocks the Netty event loop.
 *
 * <p>Khác bản chặn: khi từ chối, script trả thêm số mili giây đến khi lượt cũ nhất rời cửa sổ (điểm của lượt cũ nhất cộng độ dài cửa
 * sổ trừ bây giờ) để {@code Retry-After} đúng. Lệnh đếm có thời gian chờ ngắn: Redis lỗi, không trả lời hay không với tới được thì mở
 * cửa kèm cảnh báo, để lớp bảo vệ không làm treo hay sập yêu cầu (rate-limiting-layers.md mục 5).
 */
@Slf4j
public class ReactiveRedisRateLimiter implements ReactiveRateLimiter {

    /** Trả {1, 0} khi cho qua; {0, mili giây chờ} khi từ chối. */
    private static final DefaultRedisScript<List> SCRIPT = new DefaultRedisScript<>("""
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
                return {1, 0}
            end
            local oldest = redis.call('ZRANGE', key, 0, 0, 'WITHSCORES')
            local retry_ms = tonumber(oldest[2]) + ttl_ms - now
            if retry_ms < 1 then retry_ms = 1 end
            return {0, retry_ms}
            """, List.class);

    private final ReactiveStringRedisTemplate redisTemplate;
    private final Duration commandTimeout;

    public ReactiveRedisRateLimiter(ReactiveStringRedisTemplate redisTemplate, Duration commandTimeout) {
        this.redisTemplate = redisTemplate;
        this.commandTimeout = commandTimeout;
    }

    @Override
    public Mono<RateLimitDecision> acquire(String key, int limit, Duration window) {
        long now         = System.currentTimeMillis();
        long windowStart = now - window.toMillis();
        AtomicBoolean abandoned = new AtomicBoolean();

        // Lấy kết nối Redis có thể chặn luồng (lần đầu hoặc khi Redis chết, Lettuce chờ kết nối): đưa sang bộ lập lịch riêng để không chặn
        // luồng sự kiện của Netty; thời gian chờ chạy độc lập nên vẫn mở cửa đúng hạn (đã gặp: Redis im lặng làm cả lời gọi treo 10 giây).
        return Mono.defer(() -> redisTemplate.execute(
                        SCRIPT,
                        List.of(key),
                        List.of(String.valueOf(now), String.valueOf(windowStart),
                                String.valueOf(limit), String.valueOf(window.toMillis())))
                .next())
                .subscribeOn(Schedulers.boundedElastic())
                .map(ReactiveRedisRateLimiter::toDecision)
                .defaultIfEmpty(RateLimitDecision.allow())
                // Lỗi của chính lần gọi (kể cả lỗi kết nối báo muộn sau khi đã hết giờ) được xử lý tại đây, nếu không lỗi báo muộn tới một bên đã hủy
                // và Reactor ghi ERROR "onErrorDropped" (đã gặp ở lần gọi đầu sau khi khởi động, kết nối chậm hơn thời gian chờ).
                .onErrorResume(ex -> failOpen(key, ex, abandoned.get()))
                .timeout(commandTimeout)
                .onErrorResume(ex -> {
                    abandoned.set(true);
                    return failOpen(key, ex, false);
                });
    }

    private static Mono<RateLimitDecision> failOpen(String key, Throwable ex, boolean alreadyReported) {
        if (alreadyReported) {
            log.debug("[ReactiveRedisRateLimiter] late failure after the fail-open for key={}: {}", key, ex.toString());
        } else {
            log.warn("[ReactiveRedisRateLimiter] Redis unavailable or too slow, failing open for key={}: {}", key, ex.toString());
        }
        return Mono.just(RateLimitDecision.allow());
    }

    private static RateLimitDecision toDecision(List<?> result) {
        boolean allowed = Long.valueOf(1L).equals(((Number) result.get(0)).longValue());
        return allowed ? RateLimitDecision.allow() : RateLimitDecision.deny(Duration.ofMillis(((Number) result.get(1)).longValue()));
    }
}
