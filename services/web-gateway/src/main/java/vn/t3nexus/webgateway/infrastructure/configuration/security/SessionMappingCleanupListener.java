package vn.t3nexus.webgateway.infrastructure.configuration.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;

import static vn.t3nexus.webgateway.infrastructure.configuration.security.SessionMappingAuthenticationSuccessHandler.WEBGW_SESSION_KEY_PREFIX;

/**
 * Cleans up Redis mapping keys when a Spring Session is destroyed.
 *
 * Called by:
 *  - RedisConfiguration.sessionExpiryContainer — via keyspace notification when session TTL expires
 *  - WebGatewayLogoutSuccessHandler — on explicit logout
 *  - SessionRevokeController — on back-channel revocation
 *
 * The Lua script is atomic: no race between GET(ossId) and DEL.
 * Idempotent: if mappings were already removed, GET returns nil and script is a no-op.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SessionMappingCleanupListener {

    private static final RedisScript<String> CLEANUP_SCRIPT = RedisScript.of("""
            local ossId = redis.call('GET', KEYS[1])
            if ossId then
              redis.call('DEL', 'webgw:oauth:' .. ossId)
              redis.call('DEL', KEYS[1])
            end
            return ossId
            """, String.class);

    private final ReactiveStringRedisTemplate redisTemplate;

    public Mono<Void> cleanupBySessionId(String sessionId) {
        String reverseKey = WEBGW_SESSION_KEY_PREFIX + sessionId;
        return redisTemplate.execute(CLEANUP_SCRIPT, List.of(reverseKey))
                .doOnNext(ossId -> log.info("[SessionCleanup] cleared mappings: session={}, ossId={}", sessionId, ossId))
                .next()
                .then();
    }
}
