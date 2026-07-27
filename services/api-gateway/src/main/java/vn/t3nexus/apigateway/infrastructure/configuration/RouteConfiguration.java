package vn.t3nexus.apigateway.infrastructure.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * api-gateway routing thuần — mỗi client type ứng với đúng 1 downstream, path-prefix strip 1 segment.
 * Không TokenRelay, không session, không business logic — xem adr/009-mobile-gateway.md.
 * <p>
 * /web/**    → web-gateway    (browser BFF — session cookie, business API + Spring Security
 *                               OAuth2 Client callback paths: /login/oauth2/code/**,
 *                               /oauth2/authorization/** đều nằm gọn dưới prefix này)
 * /mobile/** → mobile-gateway (mobile BFF — chưa có service thật, route giữ chỗ theo ADR-009)
 * /auth/**   → oauth2-service (login form, MFA, register, OIDC logout — bypass cả 2 BFF)
 */
@Configuration
public class RouteConfiguration {

    @Value("${apigateway.routes.web-gateway.uri}")
    private String webGatewayUri;

    @Value("${apigateway.routes.mobile-gateway.uri}")
    private String mobileGatewayUri;

    @Value("${apigateway.routes.oauth2-service.uri}")
    private String oauth2ServiceUri;

    /**
     * Địa chỉ public mà browser thực sự gõ vào — dùng để set X-Forwarded-Host/Proto thủ công.
     * Spring Cloud Gateway server-webflux KHÔNG tự inject 2 header này (đã verify thực tế: thiếu
     * chúng, {baseUrl} phía downstream vẫn resolve ra host:port nội bộ dù X-Forwarded-Prefix đã set
     * đúng) — khác với X-Forwarded-For vốn có global filter riêng tự thêm.
     */
    @Value("${apigateway.public-host}")
    private String publicHost;

    @Value("${apigateway.public-proto}")
    private String publicProto;

    @Bean
    public RouteLocator gateway(RouteLocatorBuilder builder) {
        return builder.routes()
                .route("web-gateway", rs -> rs
                        .path("/web/**")
                        .filters(f -> f
                                .stripPrefix(1)
                                .addRequestHeader("X-Forwarded-Prefix", "/web")
                                .addRequestHeader("X-Forwarded-Host", publicHost)
                                .addRequestHeader("X-Forwarded-Proto", publicProto)
                                .dedupeResponseHeader(
                                        "Access-Control-Allow-Origin Access-Control-Allow-Credentials "
                                                + "Access-Control-Expose-Headers Vary",
                                        "RETAIN_FIRST"))
                        .uri(webGatewayUri))
                .route("mobile-gateway", rs -> rs
                        .path("/mobile/**")
                        .filters(f -> f
                                .stripPrefix(1)
                                .addRequestHeader("X-Forwarded-Prefix", "/mobile")
                                .addRequestHeader("X-Forwarded-Host", publicHost)
                                .addRequestHeader("X-Forwarded-Proto", publicProto)
                                .dedupeResponseHeader(
                                        "Access-Control-Allow-Origin Access-Control-Allow-Credentials "
                                                + "Access-Control-Expose-Headers Vary",
                                        "RETAIN_FIRST"))
                        .uri(mobileGatewayUri))
                .route("oauth2-service", rs -> rs
                        .path("/auth/**")
                        .filters(f -> f
                                .stripPrefix(1)
                                .addRequestHeader("X-Forwarded-Prefix", "/auth")
                                .addRequestHeader("X-Forwarded-Host", publicHost)
                                .addRequestHeader("X-Forwarded-Proto", publicProto)
                                .dedupeResponseHeader(
                                        "Access-Control-Allow-Origin Access-Control-Allow-Credentials "
                                                + "Access-Control-Expose-Headers Vary",
                                        "RETAIN_FIRST"))
                        .uri(oauth2ServiceUri))
                .build();
    }
}
