package vn.t3nexus.websocketgateway.infrastructure.ws.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.CloseStatus;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@Component
public class InAppWebSocketHandler implements WebSocketHandler {

    private final ReactiveStringRedisTemplate redisTemplate;
    private static final String TICKET_PREFIX = "wsticket:";

    private static final Logger log = LoggerFactory.getLogger(InAppWebSocketHandler.class);

    public InAppWebSocketHandler(ReactiveStringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        String ticket = extractTicket(session);

        if (ticket == null || ticket.isBlank()) {
            return session.close(CloseStatus.POLICY_VIOLATION.withReason("missing ticket"));
        }

        return redisTemplate.opsForValue()
                .getAndDelete(TICKET_PREFIX + ticket)
                .switchIfEmpty(Mono.defer(() -> session.close(CloseStatus.POLICY_VIOLATION.withReason("invalid or expired ticket"))).then(Mono.empty()))
                .flatMap(userId -> subscribeAndForward(session, userId));
    }

    private String extractTicket(WebSocketSession session) {
        List<String> values = UriComponentsBuilder
                .fromUri(session.getHandshakeInfo().getUri())
                .build()
                .getQueryParams()
                .get("ticket");

        return (values == null || values.isEmpty()) ? null : values.getFirst();
    }

    private Mono<Void> subscribeAndForward(WebSocketSession session, String userId) {
        Flux<WebSocketMessage> outbound = redisTemplate.listenToChannel("user:" + userId + ":inapp")
                .map(msg -> session.textMessage(msg.getMessage()));

        return session.send(outbound)
                .doOnSubscribe(s -> log.info("connected, userId={}", userId))
                .doFinally(signal -> log.info("disconnected, userId={}, signal={}", userId, signal));
    }
}
