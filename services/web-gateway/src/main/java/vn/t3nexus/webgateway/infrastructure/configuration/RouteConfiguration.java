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
                .build();
    }
}
