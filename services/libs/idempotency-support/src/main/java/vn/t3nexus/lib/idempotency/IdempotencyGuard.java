package vn.t3nexus.lib.idempotency;

import java.time.Duration;

/**
 * Guards against duplicate processing under Kafka at-least-once delivery.
 *
 * Usage pattern:
 * <pre>
 *   if (!guard.tryAcquire(key, ttl)) return; // duplicate — skip
 *   try {
 *       process();
 *   } catch (Exception e) {
 *       guard.release(key);  // allow retry
 *       throw e;
 *   }
 * </pre>
 */
public interface IdempotencyGuard {

    /**
     * Attempts to acquire the idempotency lock for the given key.
     *
     * @return true if acquired (first time seen), false if already processed (duplicate)
     */
    boolean tryAcquire(String key, Duration ttl);

    /**
     * Releases the lock so a failed message can be retried.
     * Only call on transient failures — permanent failures should keep the lock.
     */
    void release(String key);
}
