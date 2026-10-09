package vn.t3nexus.inappworker.application.inapp;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Publish tới đúng channel Redis Pub/Sub mà {@code websocket-gateway} đang subscribe cho user đó
 * ({@code user:{userId}:inapp}) — xem {@code notification-service/service.md} §websocket-gateway.
 * Nếu không ai subscribe (user offline), message biến mất — không lưu lại (đã có
 * {@code notification_inbox}, ghi atomically bởi notification-service, phục vụ đúng trường hợp này).
 */
@Component
@RequiredArgsConstructor
public class InappPublisher {

    private final StringRedisTemplate redisTemplate;

    public void publish(String userId, String payload) {
        redisTemplate.convertAndSend("user:" + userId + ":inapp", payload);
    }
}
