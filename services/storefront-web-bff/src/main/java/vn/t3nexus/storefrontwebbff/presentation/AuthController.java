package vn.t3nexus.storefrontwebbff.presentation;

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
import vn.t3nexus.storefrontwebbff.infrastructure.configuration.security.AccessTokenClaims;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.time.Duration;
import java.util.UUID;

/**
 * UC-001: Khởi tạo OAuth2 Authorization Code Flow từ Angular.
 * UC-006: Kiểm tra trạng thái xác thực của session hiện tại.
 */
@RestController
@RequestMapping("/bff/auth")
@Slf4j
public class AuthController {

    private static final String WS_TICKET_KEY_PREFIX = "wsticket:";
    private static final Duration WS_TICKET_TTL = Duration.ofSeconds(10);

    private final ServerOAuth2AuthorizedClientRepository authorizedClientRepository;
    private final AccessTokenClaims claims;
    private final ReactiveStringRedisTemplate redisTemplate;

    public AuthController(ServerOAuth2AuthorizedClientRepository authorizedClientRepository,
                          AccessTokenClaims claims,
                          ReactiveStringRedisTemplate redisTemplate) {
        this.authorizedClientRepository = authorizedClientRepository;
        this.claims = claims;
        this.redisTemplate = redisTemplate;
    }

    @GetMapping("/login")
    public Mono<Void> login(ServerWebExchange exchange) {
        // Đường dẫn tuyệt đối trong host, không dùng contextPath: BFF sở hữu gốc của host shop (ADR-0006), nên điểm vào không phụ thuộc
        // X-Forwarded-Prefix (cổng đã xóa tiêu đề này; nếu lọt tới đây thì cũng không được làm đổi đường dẫn). KT-36.
        exchange.getResponse().setStatusCode(HttpStatus.FOUND);
        exchange.getResponse().getHeaders().setLocation(URI.create("/oauth2/authorization/storefront-web"));
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
                                String role        = claims.text(accessToken, AccessTokenClaims.ROLE);
                                return ResponseEntity.ok(new SessionResponse(token.getName(), role));
                            })
                )
                .defaultIfEmpty(ResponseEntity.status(HttpStatus.UNAUTHORIZED).<SessionResponse>build());
    }

    /**
     * Mint 1 ticket dùng 1 lần (TTL 10s) để websocket-gateway resolve userId lúc handshake —
     * WebSocket không mang được cookie session của storefront-web-bff. Xem docs/design/services/notification-service.md.
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

    public record SessionResponse(
            String userId,
            String role
    ) {}

    public record TicketResponse(String ticket) {}
}
