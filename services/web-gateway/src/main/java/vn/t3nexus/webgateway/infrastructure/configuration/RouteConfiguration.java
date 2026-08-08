package vn.t3nexus.webgateway.infrastructure.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RouteConfiguration {

    @Value("${webgateway.routes.oauth2-service.uri}")
    private String oauth2ServiceUri;

    @Value("${webgateway.routes.identity-service.uri}")
    private String identityServiceUri;

    @Value("${webgateway.routes.customer-service.uri}")
    private String customerServiceUri;

    @Value("${webgateway.routes.catalog-service.uri}")
    private String catalogServiceUri;

    /**
     * "(/web)?" ở đầu regex — chấp nhận cả 2 dạng path:
     *   /api/{service}/**       khi gọi thẳng web-gateway (không qua api-gateway)
     *   /web/api/{service}/**   khi qua api-gateway: ForwardedHeaderTransformer (bật bởi
     *                            server.forward-headers-strategy=framework, cần cho
     *                            AuthController.login()/logout build đúng URL) phục hồi lại
     *                            "/web" vào path mà chính rewritePath này đọc — dù api-gateway
     *                            đã StripPrefix(1) trước khi forward. Không dual-pattern thì
     *                            rewritePath không match, no-op, forward sai path xuống downstream.
     */
    @Bean
    public RouteLocator gateway(RouteLocatorBuilder builder) {
        return builder.routes()
                .route("oauth2-service", rs -> rs
                        .path("/api/oauth2/**", "/web/api/oauth2/**")
                        .filters(f -> f
                                .tokenRelay()
                                .saveSession()
                                .rewritePath("(/web)?/api/oauth2/(?<segment>.*)", "/api/${segment}")
                                .dedupeResponseHeader("Access-Control-Allow-Origin", "RETAIN_FIRST")
                                .dedupeResponseHeader("Access-Control-Allow-Credentials", "RETAIN_FIRST"))
                        .uri(oauth2ServiceUri))
                .route("identity-service", rs -> rs
                        .path("/api/identity/**", "/web/api/identity/**")
                        .filters(f -> f
                                .tokenRelay()
                                .saveSession()
                                .rewritePath("(/web)?/api/identity/(?<segment>.*)", "/api/${segment}")
                                .dedupeResponseHeader("Access-Control-Allow-Origin", "RETAIN_FIRST")
                                .dedupeResponseHeader("Access-Control-Allow-Credentials", "RETAIN_FIRST"))
                        .uri(identityServiceUri))
                .route("customer-service", rs -> rs
                        .path("/api/customer/**", "/web/api/customer/**")
                        .filters(f -> f
                                .tokenRelay()
                                .saveSession()
                                .rewritePath("(/web)?/api/customer/(?<segment>.*)", "/api/${segment}")
                                .dedupeResponseHeader("Access-Control-Allow-Origin", "RETAIN_FIRST")
                                .dedupeResponseHeader("Access-Control-Allow-Credentials", "RETAIN_FIRST"))
                        .uri(customerServiceUri))
                .route("catalog-service", rs -> rs
                        .path("/api/catalog/**", "/web/api/catalog/**")
                        .filters(f -> f
                                .tokenRelay()
                                .saveSession()
                                .rewritePath("(/web)?/api/catalog/(?<segment>.*)", "/api/${segment}")
                                .dedupeResponseHeader("Access-Control-Allow-Origin", "RETAIN_FIRST")
                                .dedupeResponseHeader("Access-Control-Allow-Credentials", "RETAIN_FIRST"))
                        .uri(catalogServiceUri))
                .build();
    }
}
