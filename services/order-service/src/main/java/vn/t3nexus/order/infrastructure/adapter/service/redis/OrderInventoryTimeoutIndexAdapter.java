package vn.t3nexus.order.infrastructure.adapter.service.redis;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import vn.t3nexus.order.domain.order.OrderInventoryTimeoutIndex;

import java.time.Instant;
import java.util.List;

/**
 * Key {@code delayed:order-inventory-timeout}, member = {@code orderId}, score = deadline quy về
 * epoch giây — cùng dạng ZSET với {@code scheduler-service.RedisDueItemFinderAdapter}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderInventoryTimeoutIndexAdapter implements OrderInventoryTimeoutIndex {

    private static final String KEY = "delayed:order-inventory-timeout";

    /**
     * Claim atomic — {@code ZRANGEBYSCORE} rồi {@code ZREM} gộp trong cùng 1 script, tận dụng Redis
     * đơn luồng để chỉ 1 lần quét nhận được mỗi orderId (chặn double-cancel nếu sau này chạy nhiều
     * instance order-service) — cùng kỹ thuật {@code scheduler-service.RedisDueItemFinderAdapter}.
     */
    @SuppressWarnings("all")
    private static final DefaultRedisScript<List> POLL_DUE_SCRIPT = new DefaultRedisScript<>("""
            local key   = KEYS[1]
            local asOf  = ARGV[1]
            local limit = tonumber(ARGV[2])
            local items = redis.call('ZRANGEBYSCORE', key, '-inf', asOf, 'LIMIT', 0, limit)
            if #items > 0 then
                redis.call('ZREM', key, unpack(items))
            end
            return items
            """, List.class);

    private final StringRedisTemplate redisTemplate;

    @Override
    public void add(String orderId, Instant deadline) {
        try {
            redisTemplate.opsForZSet().add(KEY, orderId, deadline.getEpochSecond());
        } catch (RuntimeException e) {
            log.error("[OrderInventoryTimeoutIndex] add thất bại (best-effort — Lớp 2 Postgres backstop sẽ bù). orderId={}, reason={}", orderId, e.toString());
        }
    }

    @Override
    public void remove(String orderId) {
        try {
            redisTemplate.opsForZSet().remove(KEY, orderId);
        } catch (RuntimeException e) {
            log.error("[OrderInventoryTimeoutIndex] remove thất bại (best-effort — không sao, Order đã resolved nên lần quét sau tự no-op). orderId={}, reason={}", orderId, e.toString());
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<String> pollDue(Instant asOf, int limit) {
        List<String> ids = redisTemplate.execute(
                POLL_DUE_SCRIPT,
                List.of(KEY),
                String.valueOf(asOf.getEpochSecond()),
                String.valueOf(limit)
        );
        return ids == null ? List.of() : ids;
    }
}
