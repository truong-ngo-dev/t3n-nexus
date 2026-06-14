package vn.t3nexus.catalog.infrastructure.crosscutting.cache;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Broadcasts a cache invalidation message to all fleet instances via Redis pub/sub.
 * Each instance's {@link LocalCacheInvalidator} receives the message and evicts L1.
 *
 * Usage in command handlers that have L1-backed caches (product, category, variant):
 *   publisher.evict(CacheNames.PRODUCT, productId);
 */
@Component
@RequiredArgsConstructor
public class CacheInvalidationPublisher {

    private final StringRedisTemplate redisTemplate;

    /** Broadcast evict một key cụ thể trên L1 toàn fleet. */
    public void evict(String cacheName, String key) {
        redisTemplate.convertAndSend(
                CacheNames.INVALIDATION_CHANNEL,
                CacheNames.evictMessage(cacheName, key));
    }

    /** Broadcast clear toàn bộ cache trên L1 toàn fleet. */
    public void clear(String cacheName) {
        redisTemplate.convertAndSend(
                CacheNames.INVALIDATION_CHANNEL,
                CacheNames.clearMessage(cacheName));
    }
}
