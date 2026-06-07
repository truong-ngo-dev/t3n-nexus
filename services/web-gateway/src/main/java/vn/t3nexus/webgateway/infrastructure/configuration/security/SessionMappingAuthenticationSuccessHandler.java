package vn.t3nexus.webgateway.infrastructure.configuration.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.web.server.ServerOAuth2AuthorizedClientRepository;
import org.springframework.security.web.server.WebFilterExchange;
import org.springframework.security.web.server.authentication.RedirectServerAuthenticationSuccessHandler;
import org.springframework.security.web.server.authentication.ServerAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Base64;

/**
 * After OAuth2 login success:
 * 1. Extracts the `oss_id` claim (OAuthSession.id) from the JWT access token
 * 2. Stores bidirectional mapping in Redis with TTL:
 *    webgw:oauth:{oss_id}        → {spring_session_id}
 *    webgw:session:{session_id}  → {oss_id}
 */
@Slf4j
@Component
public class SessionMappingAuthenticationSuccessHandler implements ServerAuthenticationSuccessHandler {

    public static final String WEBGW_OAUTH_KEY_PREFIX   = "webgw:oauth:";
    public static final String WEBGW_SESSION_KEY_PREFIX = "webgw:session:";

    private final ReactiveStringRedisTemplate           redisTemplate;
    private final ServerOAuth2AuthorizedClientRepository authorizedClientRepository;
    private final ObjectMapper                           objectMapper;
    private final ServerAuthenticationSuccessHandler     delegate;

    public SessionMappingAuthenticationSuccessHandler(
            ReactiveStringRedisTemplate redisTemplate,
            ServerOAuth2AuthorizedClientRepository authorizedClientRepository,
            ObjectMapper objectMapper,
            @Value("${app.login.success-redirect-uri:/}") String successRedirectUri) {
        this.redisTemplate              = redisTemplate;
        this.authorizedClientRepository = authorizedClientRepository;
        this.objectMapper               = objectMapper;
        this.delegate                   = new RedirectServerAuthenticationSuccessHandler(successRedirectUri);
    }

    @Override
    @SuppressWarnings("all")
    public Mono<Void> onAuthenticationSuccess(WebFilterExchange webFilterExchange, Authentication authentication) {
        if (!(authentication instanceof OAuth2AuthenticationToken token)) {
            return delegate.onAuthenticationSuccess(webFilterExchange, authentication);
        }

        return authorizedClientRepository
                .loadAuthorizedClient(token.getAuthorizedClientRegistrationId(), authentication, webFilterExchange.getExchange())
                .cast(OAuth2AuthorizedClient.class)
                .flatMap(client -> {
                    String ossId = extractOssIdFromJwt(client.getAccessToken().getTokenValue());
                    if (ossId == null) {
                        log.warn("[SessionMapping] oss_id claim missing from access token — mapping skipped");
                        return Mono.empty();
                    }
                    return webFilterExchange.getExchange().getSession()
                            .flatMap(session -> {
                                String oauthKey   = WEBGW_OAUTH_KEY_PREFIX + ossId;
                                String reverseKey = WEBGW_SESSION_KEY_PREFIX + session.getId();
                                return redisTemplate.opsForValue().set(oauthKey, session.getId())
                                        .then(redisTemplate.opsForValue().set(reverseKey, ossId));
                            });
                })
                .then(delegate.onAuthenticationSuccess(webFilterExchange, authentication));
    }

    private String extractOssIdFromJwt(String jwtToken) {
        try {
            String[] parts = jwtToken.split("\\.");
            if (parts.length < 2) return null;
            byte[] payloadBytes = Base64.getUrlDecoder().decode(addPadding(parts[1]));
            JsonNode payload    = objectMapper.readTree(payloadBytes);
            JsonNode ossId      = payload.get("oss_id");
            return ossId != null && !ossId.isNull() ? ossId.asText() : null;
        } catch (Exception e) {
            log.warn("[SessionMapping] Failed to extract oss_id from JWT: {}", e.getMessage());
            return null;
        }
    }

    private String addPadding(String base64Url) {
        int mod = base64Url.length() % 4;
        if (mod == 0) return base64Url;
        return base64Url + "=".repeat(4 - mod);
    }
}
