package vn.t3nexus.webgateway.presentation;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.web.server.ServerOAuth2AuthorizedClientRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * UC-001: Khởi tạo OAuth2 Authorization Code Flow từ Angular.
 * UC-006: Kiểm tra trạng thái xác thực của session hiện tại.
 */
@RestController
@RequestMapping("/webgw/auth")
@Slf4j
public class AuthController {

    private final ServerOAuth2AuthorizedClientRepository authorizedClientRepository;
    private final ObjectMapper objectMapper;

    public AuthController(ServerOAuth2AuthorizedClientRepository authorizedClientRepository,
                          ObjectMapper objectMapper) {
        this.authorizedClientRepository = authorizedClientRepository;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/login")
    public Mono<Void> login(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.FOUND);
        exchange.getResponse().getHeaders().setLocation(URI.create("/oauth2/authorization/web-gateway"));
        return exchange.getResponse().setComplete();
    }

    /**
     * UC-006: Kiểm tra session hợp lệ mà không trigger redirect.
     * 200 + { sub, requiresProfileCompletion, contexts } → session hợp lệ, 401 → chưa xác thực.
     */
    @GetMapping("/session")
    public Mono<ResponseEntity<SessionResponse>> session(ServerWebExchange exchange) {
        return exchange.getPrincipal()
                .cast(OAuth2AuthenticationToken.class)
                .flatMap(token ->
                    authorizedClientRepository
                            .loadAuthorizedClient(
                                    token.getAuthorizedClientRegistrationId(),
                                    token,
                                    exchange)
                            .cast(OAuth2AuthorizedClient.class)
                            .map(client -> {
                                String accessToken = client.getAccessToken().getTokenValue();
                                List<Map<String, Object>> contexts = extractContexts(accessToken);

                                boolean requiresProfileCompletion = Boolean.TRUE.equals(
                                        token.getPrincipal().getAttribute("requires_profile_completion"));

                                return ResponseEntity.ok(new SessionResponse(
                                        token.getName(),
                                        requiresProfileCompletion,
                                        contexts
                                ));
                            })
                )
                .defaultIfEmpty(ResponseEntity.status(HttpStatus.UNAUTHORIZED).<SessionResponse>build());
    }

    private List<Map<String, Object>> extractContexts(String jwtToken) {
        try {
            String[] parts = jwtToken.split("\\.");
            if (parts.length < 2) return List.of();
            byte[] payloadBytes = Base64.getUrlDecoder().decode(addPadding(parts[1]));
            JsonNode payload = objectMapper.readTree(payloadBytes);
            JsonNode contextsNode = payload.get("contexts");
            if (contextsNode == null || !contextsNode.isArray()) return List.of();

            List<Map<String, Object>> result = new ArrayList<>();
            for (JsonNode item : contextsNode) {
                Map<String, Object> ctx = new LinkedHashMap<>();
                if (item.has("scope"))
                    ctx.put("scope", item.get("scope").asText());
                if (item.has("orgId") && !item.get("orgId").isNull())
                    ctx.put("orgId", item.get("orgId").asText());
                else
                    ctx.put("orgId", null);
                if (item.has("displayName"))
                    ctx.put("displayName", item.get("displayName").asText());
                if (item.has("roles")) {
                    List<String> roles = new ArrayList<>();
                    item.get("roles").forEach(r -> roles.add(r.asText()));
                    ctx.put("roles", roles);
                }
                result.add(ctx);
            }
            return result;
        } catch (Exception e) {
            log.warn("[AuthController] Failed to extract contexts from JWT: {}", e.getMessage());
            return List.of();
        }
    }

    private String addPadding(String base64Url) {
        int mod = base64Url.length() % 4;
        if (mod == 0) return base64Url;
        return base64Url + "=".repeat(4 - mod);
    }

    record SessionResponse(
            String sub,
            boolean requiresProfileCompletion,
            List<Map<String, Object>> contexts
    ) {}
}
