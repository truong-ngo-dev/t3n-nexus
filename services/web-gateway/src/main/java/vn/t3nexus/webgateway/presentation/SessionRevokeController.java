package vn.t3nexus.webgateway.presentation;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * Internal endpoint called by oauth2-service back-channel logout notification.
 *
 * KEY MAPPING (TODO): Planned to look up webgw:oauth:{sid} → springSessionId in Redis
 * and delete the corresponding Spring Session. Stubbed until mapping is implemented.
 */
@RestController
@RequestMapping("/webgw/internal/sessions")
public class SessionRevokeController {

    // [KEY MAPPING - TODO]
    // private static final String WEBGW_OAUTH_KEY_PREFIX = "webgw:oauth:";
    // private final ReactiveStringRedisTemplate redisTemplate;
    // private final ReactiveSessionRepository<? extends Session> sessionRepository;
    // [/KEY MAPPING]

    @PostMapping("/revoke")
    public Mono<ResponseEntity<Void>> revoke(@RequestBody RevokeRequest request) {
        // [KEY MAPPING - TODO]
        // String oauthKey = WEBGW_OAUTH_KEY_PREFIX + request.sid();
        // return redisTemplate.opsForValue().get(oauthKey)
        //         .flatMap(springSessionId -> {
        //             String sessionKey = SessionMappingAuthenticationSuccessHandler.WEBGW_SESSION_KEY_PREFIX + springSessionId;
        //             return sessionRepository.deleteById(springSessionId)
        //                     .then(redisTemplate.delete(oauthKey, sessionKey));
        //         })
        //         .then(Mono.just(ResponseEntity.<Void>ok().build()));
        // [/KEY MAPPING]

        return Mono.just(ResponseEntity.<Void>ok().build());
    }

    public record RevokeRequest(String sid) {}
}
