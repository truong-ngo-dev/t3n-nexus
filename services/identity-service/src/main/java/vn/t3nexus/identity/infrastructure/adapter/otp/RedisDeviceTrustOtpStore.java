package vn.t3nexus.identity.infrastructure.adapter.otp;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import vn.t3nexus.identity.application.device.trust_otp.DeviceTrustOtpStore;

import java.time.Duration;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class RedisDeviceTrustOtpStore implements DeviceTrustOtpStore {

    private static final Duration TTL          = Duration.ofMinutes(5);
    private static final int      MAX_ATTEMPTS = 3;

    private final StringRedisTemplate redisTemplate;

    @Override
    public void save(String userId, String deviceId, String otpHash) {
        redisTemplate.opsForValue().set(hashKey(userId, deviceId),     otpHash, TTL);
        redisTemplate.opsForValue().set(attemptsKey(userId, deviceId), "0",     TTL);
    }

    @Override
    public Optional<String> findHash(String userId, String deviceId) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(hashKey(userId, deviceId)));
    }

    @Override
    public int incrementAttempts(String userId, String deviceId) {
        Long count = redisTemplate.opsForValue().increment(attemptsKey(userId, deviceId));
        return count == null ? MAX_ATTEMPTS : count.intValue();
    }

    @Override
    public void delete(String userId, String deviceId) {
        redisTemplate.delete(hashKey(userId, deviceId));
        redisTemplate.delete(attemptsKey(userId, deviceId));
    }

    private static String hashKey(String userId, String deviceId) {
        return "trust_device_otp:" + userId + ":" + deviceId;
    }

    private static String attemptsKey(String userId, String deviceId) {
        return "trust_device_otp_attempts:" + userId + ":" + deviceId;
    }
}
