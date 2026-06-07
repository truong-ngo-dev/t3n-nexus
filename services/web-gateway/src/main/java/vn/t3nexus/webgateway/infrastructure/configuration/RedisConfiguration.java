package vn.t3nexus.webgateway.infrastructure.configuration;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.ReactiveRedisMessageListenerContainer;
import org.springframework.session.data.redis.config.annotation.web.server.EnableRedisWebSession;
import org.springframework.web.server.session.CookieWebSessionIdResolver;
import org.springframework.web.server.session.WebSessionIdResolver;
import reactor.core.publisher.Mono;
import vn.t3nexus.webgateway.infrastructure.configuration.security.SessionMappingCleanupListener;

@Slf4j
@Configuration
@EnableRedisWebSession
public class RedisConfiguration {

    // ReactiveRedisSessionRepository default namespace = "spring:session"
    // Session hash key pattern: spring:session:sessions:{sessionId}
    private static final String SESSION_KEY_PREFIX = "spring:session:sessions:";

    /**
     * Distinct cookie name prevents the browser from conflating this service's session
     * with the oauth2-service SESSION cookie — both run on localhost and browsers share
     * cookie scope across ports, which would overwrite the PKCE verifier on callback.
     */
    @Bean
    public WebSessionIdResolver webSessionIdResolver() {
        CookieWebSessionIdResolver resolver = new CookieWebSessionIdResolver();
        resolver.setCookieName("WEBGW_SESSION");
        return resolver;
    }

    /**
     * Subscribes to Redis keyspace "expired" events for session keys.
     * Requires Redis server configured with: notify-keyspace-events Kx
     *
     * When ReactiveRedisSessionRepository's session hash expires:
     *   channel: __keyspace@{db}__:spring:session:sessions:{sessionId}
     *   message: "expired"
     *
     * Triggers SessionMappingCleanupListener to atomically remove the
     * webgw:oauth:{ossId} and webgw:session:{sessionId} mapping keys.
     */
    @Bean
    public ReactiveRedisMessageListenerContainer sessionExpiryContainer(
            ReactiveRedisConnectionFactory factory,
            SessionMappingCleanupListener cleanupListener) {

        ReactiveRedisMessageListenerContainer container =
                new ReactiveRedisMessageListenerContainer(factory);

        container.receive(PatternTopic.of("__keyspace@*__:" + SESSION_KEY_PREFIX + "*"))
                .filter(msg -> "expired".equals(msg.getMessage()))
                .map(msg -> msg.getChannel().substring(
                        msg.getChannel().indexOf(SESSION_KEY_PREFIX) + SESSION_KEY_PREFIX.length()))
                .flatMap(sessionId -> cleanupListener.cleanupBySessionId(sessionId)
                        .doOnError(err -> log.error("[SessionExpiry] cleanup failed for session={}: {}", sessionId, err.getMessage()))
                        .onErrorResume(e -> Mono.empty()))
                .subscribe();

        return container;
    }
}
