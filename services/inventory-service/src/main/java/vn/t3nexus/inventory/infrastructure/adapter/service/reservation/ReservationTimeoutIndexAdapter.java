package vn.t3nexus.inventory.infrastructure.adapter.service.reservation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import vn.t3nexus.inventory.domain.reservation.ReservationTimeoutIndex;

import java.time.Instant;
import java.util.List;

/**
 * Key {@code delayed:reservation-timeout}, member = {@code orderId}, score = {@code expiresAt} quy về
 * epoch giây — cùng kỹ thuật {@code order-service.OrderInventoryTimeoutIndexAdapter}/
 * {@code scheduler-service.RedisDueItemFinderAdapter}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReservationTimeoutIndexAdapter implements ReservationTimeoutIndex {

    private static final String KEY = "delayed:reservation-timeout";

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
    public void add(String orderId, Instant expiresAt) {
        try {
            redisTemplate.opsForZSet().add(KEY, orderId, expiresAt.getEpochSecond());
        } catch (RuntimeException e) {
            log.error("[ReservationTimeoutIndex] add thất bại (best-effort — Lớp 2 Postgres backstop sẽ bù). orderId={}, reason={}", orderId, e.toString());
        }
    }

    @Override
    public void remove(String orderId) {
        try {
            redisTemplate.opsForZSet().remove(KEY, orderId);
        } catch (RuntimeException e) {
            log.error("[ReservationTimeoutIndex] remove thất bại (best-effort — không sao, reservation đã resolved nên lần quét sau tự no-op). orderId={}, reason={}", orderId, e.toString());
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
