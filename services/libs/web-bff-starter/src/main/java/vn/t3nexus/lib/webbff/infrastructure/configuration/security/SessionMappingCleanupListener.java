package vn.t3nexus.lib.webbff.infrastructure.configuration.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import vn.t3nexus.lib.webbff.BffProperties;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Dọn khóa ánh xạ khi phiên BFF hết hạn ở Redis (sự kiện keyspace, xem {@code RedisConfiguration.sessionExpiryContainer}). Các đường xóa
 * khác (đăng xuất, nhận báo, thay phiên khi xác thực im lặng) đã xóa ánh xạ qua {@link SessionMappingStore}.
 * <p>
 * Script Lua chạy nguyên tử và chỉ xóa khóa {@code <ứng dụng>:sid:<sid>} nếu nó vẫn trỏ tới đúng phiên vừa hết hạn: khi {@code sid} đã
 * được ghi đè sang phiên mới thì phiên cũ hết hạn muộn không được xóa ánh xạ của phiên mới. Lũy đẳng: khóa ngược đã mất thì không làm gì.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SessionMappingCleanupListener {

    private static final RedisScript<String> CLEANUP_SCRIPT = RedisScript.of("""
            local sid = redis.call('GET', KEYS[1])
            if sid then
              if redis.call('GET', ARGV[2] .. sid) == ARGV[1] then
                redis.call('DEL', ARGV[2] .. sid)
              end
              redis.call('DEL', KEYS[1])
            end
            return sid
            """, String.class);

    private final ReactiveStringRedisTemplate redisTemplate;
    private final SessionMappingStore         mappings;
    private final BffProperties               properties;

    public Mono<Void> cleanupBySessionId(String sessionId) {
        return redisTemplate.execute(CLEANUP_SCRIPT, List.of(mappings.sessionKey(sessionId)), List.of(sessionId, properties.keyPrefix() + "sid:"))
                .doOnNext(sid -> log.info("[SessionCleanup] cleared mappings: session={}, sid={}", sessionId, sid))
                .next()
                .then();
    }
}
