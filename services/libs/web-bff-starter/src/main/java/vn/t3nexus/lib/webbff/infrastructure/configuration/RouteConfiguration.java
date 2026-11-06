package vn.t3nexus.lib.webbff.infrastructure.configuration;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import vn.t3nexus.lib.webbff.BffProperties;

import java.util.Map;

@Configuration
public class RouteConfiguration {

    private static final String SERVICE_SUFFIX = "-service";

    /**
     * Một dạng đường dẫn duy nhất cho mỗi dịch vụ khai báo ở {@code bff.routes.<dịch vụ>.uri}: {@code /api/<tên bỏ hậu tố -service>/**}
     * được viết lại thành {@code /api/**}, kèm token của phiên. Sau ADR-0006 BFF sở hữu gốc của host, {@code api-gateway} không cắt tiền
     * tố và không gửi {@code X-Forwarded-Prefix} nên không còn dạng {@code /web/api/...}.
     */
    @Bean
    public RouteLocator gateway(RouteLocatorBuilder builder, BffProperties properties) {
        RouteLocatorBuilder.Builder routes = builder.routes();
        for (Map.Entry<String, BffProperties.Route> e : properties.getRoutes().entrySet()) {
            String service = e.getKey();
            String segment = service.endsWith(SERVICE_SUFFIX) ? service.substring(0, service.length() - SERVICE_SUFFIX.length()) : service;
            String prefix  = "/api/" + segment + "/";
            routes.route(service, rs -> rs
                    .path(prefix + "**")
                    .filters(f -> f
                            .tokenRelay()
                            .saveSession()
                            .rewritePath(prefix + "(?<segment>.*)", "/api/${segment}")
                            .dedupeResponseHeader("Access-Control-Allow-Origin", "RETAIN_FIRST")
                            .dedupeResponseHeader("Access-Control-Allow-Credentials", "RETAIN_FIRST"))
                    .uri(e.getValue().getUri()));
        }
        return routes.build();
    }
}
