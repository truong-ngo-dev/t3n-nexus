package vn.t3nexus.storefrontwebbff.infrastructure.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RouteConfiguration {

    @Value("${storefront.routes.oauth2-service.uri}")
    private String oauth2ServiceUri;

    @Value("${storefront.routes.identity-service.uri}")
    private String identityServiceUri;

    @Value("${storefront.routes.customer-service.uri}")
    private String customerServiceUri;

    @Value("${storefront.routes.catalog-service.uri}")
    private String catalogServiceUri;

    @Value("${storefront.routes.order-service.uri}")
    private String orderServiceUri;

    /**
     * Một dạng đường dẫn duy nhất: {@code /api/{dịch vụ}/**} được viết lại thành {@code /api/**}. Sau ADR-0006 BFF sở hữu gốc của host
     * {@code shop}, {@code api-gateway} không cắt tiền tố và không gửi {@code X-Forwarded-Prefix} nên không còn dạng {@code /web/api/...}.
     */
    @Bean
    public RouteLocator gateway(RouteLocatorBuilder builder) {
        return builder.routes()
                .route("oauth2-service", rs -> rs
                        .path("/api/oauth2/**")
                        .filters(f -> f
                                .tokenRelay()
                                .saveSession()
                                .rewritePath("/api/oauth2/(?<segment>.*)", "/api/${segment}")
                                .dedupeResponseHeader("Access-Control-Allow-Origin", "RETAIN_FIRST")
                                .dedupeResponseHeader("Access-Control-Allow-Credentials", "RETAIN_FIRST"))
                        .uri(oauth2ServiceUri))
                .route("identity-service", rs -> rs
                        .path("/api/identity/**")
                        .filters(f -> f
                                .tokenRelay()
                                .saveSession()
                                .rewritePath("/api/identity/(?<segment>.*)", "/api/${segment}")
                                .dedupeResponseHeader("Access-Control-Allow-Origin", "RETAIN_FIRST")
                                .dedupeResponseHeader("Access-Control-Allow-Credentials", "RETAIN_FIRST"))
                        .uri(identityServiceUri))
                .route("customer-service", rs -> rs
                        .path("/api/customer/**")
                        .filters(f -> f
                                .tokenRelay()
                                .saveSession()
                                .rewritePath("/api/customer/(?<segment>.*)", "/api/${segment}")
                                .dedupeResponseHeader("Access-Control-Allow-Origin", "RETAIN_FIRST")
                                .dedupeResponseHeader("Access-Control-Allow-Credentials", "RETAIN_FIRST"))
                        .uri(customerServiceUri))
                .route("catalog-service", rs -> rs
                        .path("/api/catalog/**")
                        .filters(f -> f
                                .tokenRelay()
                                .saveSession()
                                .rewritePath("/api/catalog/(?<segment>.*)", "/api/${segment}")
                                .dedupeResponseHeader("Access-Control-Allow-Origin", "RETAIN_FIRST")
                                .dedupeResponseHeader("Access-Control-Allow-Credentials", "RETAIN_FIRST"))
                        .uri(catalogServiceUri))
                .route("order-service", rs -> rs
                        .path("/api/order/**")
                        .filters(f -> f
                                .tokenRelay()
                                .saveSession()
                                .rewritePath("/api/order/(?<segment>.*)", "/api/${segment}")
                                .dedupeResponseHeader("Access-Control-Allow-Origin", "RETAIN_FIRST")
                                .dedupeResponseHeader("Access-Control-Allow-Credentials", "RETAIN_FIRST"))
                        .uri(orderServiceUri))
                .build();
    }
}
