package vn.t3nexus.webgateway.presentation;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
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
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;

/**
 * UC-001: Khởi tạo OAuth2 Authorization Code Flow từ Angular.
 * UC-006: Kiểm tra trạng thái xác thực của session hiện tại.
 */
@RestController
@RequestMapping("/webgw/auth")
@Slf4j
public class AuthController {

    private static final String WS_TICKET_KEY_PREFIX = "wsticket:";
    private static final Duration WS_TICKET_TTL = Duration.ofSeconds(10);

    private final ServerOAuth2AuthorizedClientRepository authorizedClientRepository;
    private final ObjectMapper objectMapper;
    private final ReactiveStringRedisTemplate redisTemplate;

    public AuthController(ServerOAuth2AuthorizedClientRepository authorizedClientRepository,
                          ObjectMapper objectMapper,
                          ReactiveStringRedisTemplate redisTemplate) {
        this.authorizedClientRepository = authorizedClientRepository;
        this.objectMapper = objectMapper;
        this.redisTemplate = redisTemplate;
    }

    @GetMapping("/login")
    public Mono<Void> login(ServerWebExchange exchange) {
        // contextPath rỗng khi gọi thẳng web-gateway; = "/web" khi qua api-gateway
        // (ForwardedHeaderTransformer đọc X-Forwarded-Prefix — xem RouteConfiguration bên api-gateway).
        String contextPath = exchange.getRequest().getPath().contextPath().value();
        exchange.getResponse().setStatusCode(HttpStatus.FOUND);
        exchange.getResponse().getHeaders().setLocation(URI.create(contextPath + "/oauth2/authorization/web-gateway"));
        return exchange.getResponse().setComplete();
    }

    /**
     * UC-006: Kiểm tra session hợp lệ mà không trigger redirect.
     * 200 + { userId, role, requiresProfileCompletion } → session hợp lệ, 401 → chưa xác thực.
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
                                String role        = extractRole(accessToken);
                                return ResponseEntity.ok(new SessionResponse(token.getName(), role));
                            })
                )
                .defaultIfEmpty(ResponseEntity.status(HttpStatus.UNAUTHORIZED).<SessionResponse>build());
    }

    /**
     * Mint 1 ticket dùng 1 lần (TTL 10s) để websocket-gateway resolve userId lúc handshake —
     * WebSocket không mang được cookie session của web-gateway. Xem docs/design/services/notification-service.md.
     */
    @GetMapping("/ws-ticket")
    public Mono<ResponseEntity<TicketResponse>> wsTicket(ServerWebExchange exchange) {
        return exchange.getPrincipal()
                .cast(OAuth2AuthenticationToken.class)
                .flatMap(token -> {
                    String ticket = UUID.randomUUID().toString();
                    return redisTemplate.opsForValue()
                            .set(WS_TICKET_KEY_PREFIX + ticket, token.getName(), WS_TICKET_TTL)
                            .map(ok -> ResponseEntity.ok(new TicketResponse(ticket)));
                })
                .defaultIfEmpty(ResponseEntity.status(HttpStatus.UNAUTHORIZED).<TicketResponse>build());
    }

    private String extractRole(String jwtToken) {
        try {
            String[] parts = jwtToken.split("\\.");
            if (parts.length < 2) return null;
            byte[] payloadBytes = Base64.getUrlDecoder().decode(addPadding(parts[1]));
            JsonNode payload    = objectMapper.readTree(payloadBytes);
            JsonNode rolesNode  = payload.get("roles");
            if (rolesNode == null || !rolesNode.isArray() || rolesNode.isEmpty()) return null;
            String role = rolesNode.get(0).asString();
            return role.startsWith("ROLE_") ? role.substring(5) : role;
        } catch (Exception e) {
            log.warn("[AuthController] Failed to extract role from JWT: {}", e.getMessage());
            return null;
        }
    }

    private String addPadding(String base64Url) {
        int mod = base64Url.length() % 4;
        if (mod == 0) return base64Url;
        return base64Url + "=".repeat(4 - mod);
    }

    public record SessionResponse(
            String userId,
            String role
    ) {}

    public record TicketResponse(String ticket) {}
}
