package vn.t3nexus.webgateway.infrastructure.configuration.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.web.server.ServerOAuth2AuthorizedClientRepository;
import org.springframework.security.web.server.WebFilterExchange;
import org.springframework.security.web.server.authentication.RedirectServerAuthenticationSuccessHandler;
import org.springframework.security.web.server.authentication.ServerAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * After OAuth2 login success, delegates to the redirect handler.
 *
 * KEY MAPPING (TODO): Planned to also extract the `sid` JWT claim and store
 * webgw:oauth:{sid} → {spring_session_id} in Redis for back-channel logout support.
 * See commented block below.
 */
@Slf4j
@Component
public class SessionMappingAuthenticationSuccessHandler implements ServerAuthenticationSuccessHandler {

    // [KEY MAPPING - TODO]
    // public static final String WEBGW_OAUTH_KEY_PREFIX   = "webgw:oauth:";
    // public static final String WEBGW_SESSION_KEY_PREFIX = "webgw:session:";
    // [/KEY MAPPING]

    private final ServerOAuth2AuthorizedClientRepository authorizedClientRepository;
    private final ServerAuthenticationSuccessHandler delegate;

    // [KEY MAPPING - TODO]
    // private final ReactiveStringRedisTemplate redisTemplate;
    // private final ObjectMapper objectMapper;
    // [/KEY MAPPING]

    public SessionMappingAuthenticationSuccessHandler(
            ServerOAuth2AuthorizedClientRepository authorizedClientRepository,
            @Value("${app.login.success-redirect-uri:/}") String successRedirectUri) {
        this.authorizedClientRepository = authorizedClientRepository;
        this.delegate = new RedirectServerAuthenticationSuccessHandler(successRedirectUri);
        // [KEY MAPPING - TODO] inject ReactiveStringRedisTemplate and ObjectMapper
    }

    @Override
    public Mono<Void> onAuthenticationSuccess(WebFilterExchange webFilterExchange, Authentication authentication) {
        // [KEY MAPPING - TODO]
        // Extract sid from JWT access token and store mapping in Redis:
        //
        // if (!(authentication instanceof OAuth2AuthenticationToken token)) {
        //     return delegate.onAuthenticationSuccess(webFilterExchange, authentication);
        // }
        // return authorizedClientRepository
        //         .loadAuthorizedClient(token.getAuthorizedClientRegistrationId(), authentication, webFilterExchange.getExchange())
        //         .cast(OAuth2AuthorizedClient.class)
        //         .flatMap(client -> {
        //             String sid = extractSidFromJwt(client.getAccessToken().getTokenValue());
        //             if (sid == null) {
        //                 log.warn("No sid claim found in access token — session mapping skipped");
        //                 return Mono.empty();
        //             }
        //             return webFilterExchange.getExchange().getSession()
        //                     .flatMap(session -> {
        //                         String oauthKey   = WEBGW_OAUTH_KEY_PREFIX + sid;
        //                         String reverseKey = WEBGW_SESSION_KEY_PREFIX + session.getId();
        //                         return redisTemplate.opsForValue().set(oauthKey, session.getId())
        //                                 .then(redisTemplate.opsForValue().set(reverseKey, sid));
        //                     });
        //         })
        //         .then(delegate.onAuthenticationSuccess(webFilterExchange, authentication));
        // [/KEY MAPPING]

        return delegate.onAuthenticationSuccess(webFilterExchange, authentication);
    }

    // [KEY MAPPING - TODO]
    // private String extractSidFromJwt(String jwtToken) {
    //     try {
    //         String[] parts = jwtToken.split("\\.");
    //         if (parts.length < 2) return null;
    //         byte[] payloadBytes = Base64.getUrlDecoder().decode(addPadding(parts[1]));
    //         JsonNode payload = objectMapper.readTree(payloadBytes);
    //         JsonNode sid = payload.get("sid");
    //         return sid != null && !sid.isNull() ? sid.asString() : null;
    //     } catch (Exception e) {
    //         log.warn("Failed to extract sid from JWT: {}", e.getMessage());
    //         return null;
    //     }
    // }
    //
    // private String addPadding(String base64Url) {
    //     int mod = base64Url.length() % 4;
    //     if (mod == 0) return base64Url;
    //     return base64Url + "=".repeat(4 - mod);
    // }
    // [/KEY MAPPING]
}
