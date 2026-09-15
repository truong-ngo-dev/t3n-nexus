package vn.t3nexus.scheduler.infrastructure.adapter.service.redis;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import vn.t3nexus.scheduler.domain.scheduled_job.DueItemFinder;
import vn.t3nexus.scheduler.domain.scheduled_job.ScheduledJobId;

import java.time.Instant;
import java.util.List;

/**
 * Redis ZSET implementation — impl DUY NHẤT của {@link DueItemFinder}. Key {@code scheduler:due} (xem
 * {@code data.md} §Redis key design), member = {@code ScheduledJobId.getValue()}, score = {@code dueAt}
 * quy về epoch giây.
 *
 * <p>Ngoài phần mechanics Redis, class này còn giữ trọn 2 guarantee của contract {@link DueItemFinder}
 * cho {@code index}/{@code deindex} (xem {@link #afterCommit}):
 * <ul>
 *   <li><b>Hoãn tới sau commit</b> — nếu gọi trong transaction, đăng ký
 *       {@link TransactionSynchronization#afterCommit()} thay vì ghi ngay; rollback → không ghi. Gọi
 *       ngoài transaction ({@code IndexReconciler}, test) → ghi thẳng.</li>
 *   <li><b>Best-effort</b> — nuốt {@link RuntimeException} từ tầng ghi (log rồi bỏ qua). Postgres là
 *       nguồn sự thật, {@code IndexReconciler} sweep lại bù drift. Nuốt ở đây cũng để exception không
 *       lọt ngược lên caller của {@code commit()} (Spring propagate exception ném từ {@code afterCommit}).</li>
 * </ul>
 * {@code pollDue} KHÔNG đi qua cơ chế này — áp dụng ngay (caller là {@code IndexPoller}, không có
 * transaction nào để chờ).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisDueItemFinderAdapter implements DueItemFinder {

    private static final String KEY = "scheduler:due";

    /**
     * Claim atomic cho {@link #pollDue} — {@code ZRANGEBYSCORE} rồi {@code ZREM} gộp trong cùng 1 script,
     * tận dụng Redis chạy đơn luồng để 2 lời gọi song song (2 instance {@code scheduler-service} chạy HA)
     * không thể cùng nhận trùng 1 id — đây là ràng buộc correctness bắt buộc của contract
     * {@link DueItemFinder#pollDue}, không phải tối ưu tuỳ chọn.
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
    public void index(ScheduledJobId id, Instant dueAt) {
        afterCommit("index", id, () ->
                redisTemplate.opsForZSet().add(KEY, id.getValue(), dueAt.getEpochSecond()));
    }

    @Override
    public void deindex(ScheduledJobId id) {
        afterCommit("deindex", id, () ->
                redisTemplate.opsForZSet().remove(KEY, id.getValue()));
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<ScheduledJobId> pollDue(Instant asOf, int limit) {
        List<String> ids = redisTemplate.execute(
                POLL_DUE_SCRIPT,
                List.of(KEY),
                String.valueOf(asOf.getEpochSecond()),
                String.valueOf(limit)
        );
        return ids.stream().map(ScheduledJobId::of).toList();
    }

    /**
     * Trong transaction → hoãn {@code action} tới {@code afterCommit}; ngoài transaction → chạy ngay.
     * {@code afterCommit} (không phải {@code afterCompletion}) nên tx rollback thì {@code action} không
     * chạy — đúng ý "đừng ghi index cho thay đổi vừa rollback".
     */
    private void afterCommit(String op, ScheduledJobId id, Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            run(op, id, action);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                run(op, id, action);
            }
        });
    }

    private void run(String op, ScheduledJobId id, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException e) {
            log.error("[DueItemFinder] {} thất bại (best-effort — IndexReconciler sẽ bù). scheduledJobId={}, reason={}",
                    op, id.getValue(), e.toString());
        }
    }
}
