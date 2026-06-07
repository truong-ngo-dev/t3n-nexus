package vn.t3nexus.webgateway.presentation;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.session.ReactiveSessionRepository;
import org.springframework.session.Session;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import vn.t3nexus.webgateway.infrastructure.configuration.security.SessionMappingAuthenticationSuccessHandler;

/**
 * Internal endpoint called by oauth2-service for back-channel session revocation
 * (e.g., account locked, remote logout). Protected by service-account JWT — see
 * SecurityConfiguration internalFilterChain (SCOPE_webgw.internal required).
 */
@RestController
@RequestMapping("/webgw/internal/sessions")
@RequiredArgsConstructor
public class  SessionRevokeController {

    private final ReactiveStringRedisTemplate           redisTemplate;
    private final ReactiveSessionRepository<? extends Session> sessionRepository;

    @PostMapping("/revoke")
    public Mono<ResponseEntity<Void>> revoke(@RequestBody RevokeRequest request) {
        String oauthKey = SessionMappingAuthenticationSuccessHandler.WEBGW_OAUTH_KEY_PREFIX + request.ossId();

        return redisTemplate.opsForValue().get(oauthKey)
                .flatMap(springSessionId -> {
                    String sessionKey = SessionMappingAuthenticationSuccessHandler.WEBGW_SESSION_KEY_PREFIX + springSessionId;
                    return sessionRepository.deleteById(springSessionId)
                            .then(redisTemplate.delete(oauthKey, sessionKey));
                })
                .then(Mono.just(ResponseEntity.<Void>ok().build()));
    }

    public record RevokeRequest(String ossId) {}
}
