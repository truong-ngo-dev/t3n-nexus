package vn.t3nexus.storefrontwebbff.infrastructure.configuration.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;

import static vn.t3nexus.storefrontwebbff.infrastructure.configuration.security.SessionMappingStore.SESSION_KEY_PREFIX;

/**
 * Dọn khóa ánh xạ khi phiên BFF hết hạn ở Redis (sự kiện keyspace, xem {@code RedisConfiguration.sessionExpiryContainer}). Các đường xóa
 * khác (đăng xuất, nhận báo, thay phiên khi xác thực im lặng) đã xóa ánh xạ qua {@link SessionMappingStore}.
 * <p>
 * Script Lua chạy nguyên tử và chỉ xóa khóa {@code storefront:sid:<sid>} nếu nó vẫn trỏ tới đúng phiên vừa hết hạn: khi {@code sid} đã
 * được ghi đè sang phiên mới thì phiên cũ hết hạn muộn không được xóa ánh xạ của phiên mới. Lũy đẳng: khóa ngược đã mất thì không làm gì.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SessionMappingCleanupListener {

    private static final RedisScript<String> CLEANUP_SCRIPT = RedisScript.of("""
            local sid = redis.call('GET', KEYS[1])
            if sid then
              if redis.call('GET', 'storefront:sid:' .. sid) == ARGV[1] then
                redis.call('DEL', 'storefront:sid:' .. sid)
              end
              redis.call('DEL', KEYS[1])
            end
            return sid
            """, String.class);

    private final ReactiveStringRedisTemplate redisTemplate;

    public Mono<Void> cleanupBySessionId(String sessionId) {
        return redisTemplate.execute(CLEANUP_SCRIPT, List.of(SESSION_KEY_PREFIX + sessionId), List.of(sessionId))
                .doOnNext(sid -> log.info("[SessionCleanup] cleared mappings: session={}, sid={}", sessionId, sid))
                .next()
                .then();
    }
}
