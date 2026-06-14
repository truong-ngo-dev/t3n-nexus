package vn.t3nexus.catalog.infrastructure.crosscutting.cache;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

/**
 * Subscribes to the Redis pub/sub invalidation channel and evicts entries
 * from the in-process Caffeine (L1) cache on every instance in the fleet.
 *
 * Message format:
 *   "cacheName::key"  → evict one key
 *   "cacheName"       → clear entire cache
 */
@Slf4j
public class LocalCacheInvalidator {

    private final CacheManager caffeineCacheManager;

    public LocalCacheInvalidator(@Qualifier("caffeineCacheManager") CacheManager caffeineCacheManager) {
        this.caffeineCacheManager = caffeineCacheManager;
    }

    public void onInvalidate(String message) {
        int sep = message.indexOf("::");
        if (sep < 0) {
            clearCache(message);
        } else {
            evictKey(message.substring(0, sep), message.substring(sep + 2));
        }
    }

    private void clearCache(String cacheName) {
        Cache cache = caffeineCacheManager.getCache(cacheName);
        if (cache == null) return;
        cache.clear();
        log.debug("[L1 invalidate] cleared cache={}", cacheName);
    }

    private void evictKey(String cacheName, String key) {
        Cache cache = caffeineCacheManager.getCache(cacheName);
        if (cache == null) return;
        cache.evict(key);
        log.debug("[L1 invalidate] evicted cache={} key={}", cacheName, key);
    }
}
