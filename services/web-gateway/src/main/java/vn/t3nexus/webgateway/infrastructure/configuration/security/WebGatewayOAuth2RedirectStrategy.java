package vn.t3nexus.webgateway.infrastructure.configuration.security;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.web.server.ServerRedirectStrategy;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.Collection;
import java.util.Optional;

/**
 * Returns a 2xx status (default: 202 Accepted) instead of 302 so the Angular SPA
 * can handle the redirect manually rather than the browser following it automatically.
 */
@RequiredArgsConstructor
public class WebGatewayOAuth2RedirectStrategy implements ServerRedirectStrategy {

    private final HttpStatus defaultStatus;

    @Override
    @SuppressWarnings("all")
    public Mono<Void> sendRedirect(ServerWebExchange exchange, URI location) {
        return Mono.fromRunnable(() -> {
            ServerHttpResponse response = exchange.getResponse();
            HttpStatus status = Optional
                    .ofNullable(exchange.getRequest().getHeaders().get("X-RESPONSE-STATUS"))
                    .stream()
                    .flatMap(Collection::stream)
                    .filter(StringUtils::hasLength)
                    .findAny()
                    .map(statusStr -> {
                        try {
                            return HttpStatus.valueOf(Integer.parseInt(statusStr));
                        } catch (NumberFormatException e) {
                            return HttpStatus.valueOf(statusStr.toLowerCase());
                        }
                    })
                    .orElse(defaultStatus);
            response.setStatusCode(status);
            response.getHeaders().setLocation(location);
        });
    }
}
