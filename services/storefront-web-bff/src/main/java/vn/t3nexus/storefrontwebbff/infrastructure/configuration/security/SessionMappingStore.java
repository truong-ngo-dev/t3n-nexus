package vn.t3nexus.storefrontwebbff.infrastructure.configuration.security;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Ánh xạ hai chiều giữa phiên ứng dụng ({@code sid}, ở Xác thực) và phiên BFF (`tech.md` T6): {@code storefront:sid:<sid>} trỏ tới mã phiên
 * BFF, {@code storefront:session:<mã phiên>} trỏ ngược về {@code sid}. Một {@code sid} ứng với tối đa một phiên BFF. Hạn ánh xạ là hạn phiên
 * cộng ân hạn ({@code app.session.mapping-grace}) nên không bao giờ ngắn hơn hạn phiên; cả hai khóa đặt lại hạn mỗi lần {@link #link}.
 */
@Component
@RequiredArgsConstructor
public class SessionMappingStore {

    public static final String SID_KEY_PREFIX     = "storefront:sid:";
    public static final String SESSION_KEY_PREFIX = "storefront:session:";

    private final ReactiveStringRedisTemplate redisTemplate;

    @Value("${app.session.ttl:7d}")
    private Duration sessionTtl;

    @Value("${app.session.mapping-grace:5m}")
    private Duration mappingGrace;

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
        return redisTemplate.opsForValue().get(SID_KEY_PREFIX + sid).defaultIfEmpty("")
                .flatMap(previous -> redisTemplate.opsForValue().set(SID_KEY_PREFIX + sid, sessionId, ttl)
                        .then(redisTemplate.opsForValue().set(SESSION_KEY_PREFIX + sessionId, sid, ttl))
                        .then(previous.isEmpty() || previous.equals(sessionId)
                                ? Mono.<String>empty()
                                : redisTemplate.delete(SESSION_KEY_PREFIX + previous).thenReturn(previous)));
    }

    /** Xóa cả hai khóa của {@code sid}; trả mã phiên BFF đã ánh xạ, rỗng nếu không có ánh xạ (xóa lặp lại là vô hại). */
    public Mono<String> unlink(String sid) {
        return redisTemplate.opsForValue().get(SID_KEY_PREFIX + sid)
                .flatMap(sessionId -> redisTemplate.delete(SID_KEY_PREFIX + sid, SESSION_KEY_PREFIX + sessionId).thenReturn(sessionId));
    }

    /**
     * Xóa ánh xạ của một phiên BFF đang bị bỏ (làm mới bị từ chối): tra {@code sid} từ khóa ngược, xóa khóa ngược, và chỉ xóa
     * {@code storefront:sid:<sid>} nếu nó còn trỏ đúng phiên này (đã ghi đè sang phiên khác thì không đụng).
     */
    public Mono<Void> unlinkBySession(String sessionId) {
        return redisTemplate.opsForValue().get(SESSION_KEY_PREFIX + sessionId)
                .flatMap(sid -> redisTemplate.delete(SESSION_KEY_PREFIX + sessionId)
                        .then(redisTemplate.opsForValue().get(SID_KEY_PREFIX + sid))
                        .filter(sessionId::equals)
                        .flatMap(same -> redisTemplate.delete(SID_KEY_PREFIX + sid)))
                .then();
    }
}
