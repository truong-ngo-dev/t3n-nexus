package vn.t3nexus.lib.webbff.infrastructure.configuration.security;

import lombok.RequiredArgsConstructor;
import vn.t3nexus.lib.webbff.BffProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Ánh xạ hai chiều giữa phiên ứng dụng ({@code sid}, ở Xác thực) và phiên BFF (`tech.md` T6): {@code <ứng dụng>:sid:<sid>} trỏ tới mã phiên
 * BFF, {@code <ứng dụng>:session:<mã phiên>} trỏ ngược về {@code sid}. Một {@code sid} ứng với tối đa một phiên BFF. Hạn ánh xạ là hạn phiên
 * cộng ân hạn ({@code app.session.mapping-grace}) nên không bao giờ ngắn hơn hạn phiên; cả hai khóa đặt lại hạn mỗi lần {@link #link}.
 */
@Component
@RequiredArgsConstructor
public class SessionMappingStore {

    private final ReactiveStringRedisTemplate redisTemplate;
    private final BffProperties               properties;

    @Value("${app.session.ttl:7d}")
    private Duration sessionTtl;

    @Value("${app.session.mapping-grace:5m}")
    private Duration mappingGrace;

    /** Khóa {@code <ứng dụng>:sid:<sid>}. */
    public String sidKey(String sid) { return properties.keyPrefix() + "sid:" + sid; }

    /** Khóa {@code <ứng dụng>:session:<mã phiên>}. */
    public String sessionKey(String sessionId) { return properties.keyPrefix() + "session:" + sessionId; }

    /** Hạn của phiên BFF, đặt lại mỗi lần lưu token mới. */
    public Duration sessionTtl() {
        return sessionTtl;
    }

    /**
     * Ghi (hoặc ghi đè) ánh xạ {@code sid} ↔ phiên BFF với hạn mới. Trả mã phiên BFF cũ nếu {@code sid} đang trỏ tới một phiên khác (khóa
     * ngược của nó đã xóa; việc xóa phiên cũ do bên gọi), rỗng nếu không có gì bị thay.
     */
    public Mono<String> link(String sid, String sessionId) {
        Duration ttl = sessionTtl.plus(mappingGrace);
        return redisTemplate.opsForValue().get(sidKey(sid)).defaultIfEmpty("")
                .flatMap(previous -> redisTemplate.opsForValue().set(sidKey(sid), sessionId, ttl)
                        .then(redisTemplate.opsForValue().set(sessionKey(sessionId), sid, ttl))
                        .then(previous.isEmpty() || previous.equals(sessionId)
                                ? Mono.<String>empty()
                                : redisTemplate.delete(sessionKey(previous)).thenReturn(previous)));
    }

    /** Xóa cả hai khóa của {@code sid}; trả mã phiên BFF đã ánh xạ, rỗng nếu không có ánh xạ (xóa lặp lại là vô hại). */
    public Mono<String> unlink(String sid) {
        return redisTemplate.opsForValue().get(sidKey(sid))
                .flatMap(sessionId -> redisTemplate.delete(sidKey(sid), sessionKey(sessionId)).thenReturn(sessionId));
    }

    /**
     * Xóa ánh xạ của một phiên BFF đang bị bỏ (làm mới bị từ chối): tra {@code sid} từ khóa ngược, xóa khóa ngược, và chỉ xóa
     * {@code <ứng dụng>:sid:<sid>} nếu nó còn trỏ đúng phiên này (đã ghi đè sang phiên khác thì không đụng).
     */
    public Mono<Void> unlinkBySession(String sessionId) {
        return redisTemplate.opsForValue().get(sessionKey(sessionId))
                .flatMap(sid -> redisTemplate.delete(sessionKey(sessionId))
                        .then(redisTemplate.opsForValue().get(sidKey(sid)))
                        .filter(sessionId::equals)
                        .flatMap(same -> redisTemplate.delete(sidKey(sid))))
                .then();
    }
}
