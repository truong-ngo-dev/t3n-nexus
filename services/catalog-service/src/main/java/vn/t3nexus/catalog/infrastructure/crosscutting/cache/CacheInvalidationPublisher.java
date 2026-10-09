package vn.t3nexus.catalog.infrastructure.crosscutting.cache;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Broadcasts a cache invalidation message to all fleet instances via Redis pub/sub.
 * Each instance's {@link LocalCacheInvalidator} receives the message and evicts L1.
 *
 * Usage in command handlers that have L1-backed caches (product, category, variant):
 *   publisher.evict(CacheNames.PRODUCT, productId);
 *
 * <p>Gọi trong transaction thì message chỉ phát SAU commit. Phát trước commit thì instance nào đọc chen giữa (L1 đã
 * trống, L2 chưa đổi, DB chưa commit) sẽ nạp lại bản cũ vào L1 và giữ tới hết TTL. Message cũng tới chính instance
 * gửi, nên L1 local bị evict thêm 1 lần sau commit — xoá luôn bản cũ lỡ nạp lại trong lúc chờ commit.
 */
@Component
@RequiredArgsConstructor
public class CacheInvalidationPublisher {

    private final StringRedisTemplate redisTemplate;

    /** Broadcast evict một key cụ thể trên L1 toàn fleet. */
    public void evict(String cacheName, String key) {
        afterCommit(() -> redisTemplate.convertAndSend(
                CacheNames.INVALIDATION_CHANNEL,
                CacheNames.evictMessage(cacheName, key)));
    }

    /** Broadcast clear toàn bộ cache trên L1 toàn fleet. */
    public void clear(String cacheName) {
        afterCommit(() -> redisTemplate.convertAndSend(
                CacheNames.INVALIDATION_CHANNEL,
                CacheNames.clearMessage(cacheName)));
    }

    private static void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }
}
