package vn.t3nexus.lib.idempotency;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

@RequiredArgsConstructor
public class RedisIdempotencyGuard implements IdempotencyGuard {

    private final StringRedisTemplate redisTemplate;

    @Override
    public boolean tryAcquire(String key, Duration ttl) {
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(key, "1", ttl);
        return Boolean.TRUE.equals(acquired);
    }

    @Override
    public void release(String key) {
        redisTemplate.delete(key);
    }
}
