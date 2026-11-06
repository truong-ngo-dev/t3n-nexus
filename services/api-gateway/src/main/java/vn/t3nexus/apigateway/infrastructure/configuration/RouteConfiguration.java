package vn.t3nexus.apigateway.infrastructure.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Định tuyến theo {@code Host} (ADR-0006), không tiền tố đường dẫn, không {@code X-Forwarded-Prefix}. Cổng chỉ định tuyến thuần:
 * không TokenRelay, không phiên, không nghiệp vụ.
 * <ul>
 *   <li>host {@code auth}: toàn bộ tới Xác thực;</li>
 *   <li>host {@code shop}: đường dẫn của BFF ({@code /api/**}, {@code /oauth2/authorization/**}, {@code /login/oauth2/code/**},
 *       {@code /bff/**}) tới BFF của ứng dụng Cửa hàng, đường dẫn còn lại tới máy chủ giao diện Cửa hàng; giao diện không dùng các
 *       tiền tố của BFF;</li>
 *   <li>host {@code seller}: đường dẫn của BFF tới BFF của ứng dụng Người bán, đường dẫn còn lại tới máy chủ giao diện Người bán;</li>
 *   <li>host {@code ops}: đường dẫn của BFF tới BFF của ứng dụng Vận hành, đường dẫn còn lại tới máy chủ giao diện Vận hành; host ngoài danh sách không khớp tuyến nào nên nhận 404.</li>
 * </ul>
 * Tiêu đề {@code X-Forwarded-*} do bộ lọc có sẵn của Gateway đặt từ yêu cầu thật sau khi {@code ForwardedHeadersSanitizer} xóa giá trị
 * client tự gửi.
 */
@Configuration
@EnableConfigurationProperties(GatewayProperties.class)
public class RouteConfiguration {

    /** Mã tuyến tới máy chủ giao diện: tài nguyên tĩnh, không bị giới hạn tần suất theo địa chỉ (xem IpRateLimitFilter). */
    public static final String FRONTEND_ROUTE_ID = "storefront-frontend";

    /** Danh sách mã tuyến giao diện không bị giới hạn tần suất theo địa chỉ. */
    public static final java.util.Set<String> FRONTEND_ROUTE_IDS = java.util.Set.of("storefront-frontend", "seller-frontend", "ops-frontend");

    static final String[] BFF_PATHS = {"/api/**", "/oauth2/authorization/**", "/login/oauth2/code/**", "/bff/**"};

    private static final String DEDUPE_CORS = "Access-Control-Allow-Origin Access-Control-Allow-Credentials "
            + "Access-Control-Expose-Headers Vary";

    @Value("${apigateway.routes.oauth2-service.uri}")
    private String oauth2ServiceUri;

    @Value("${apigateway.routes.storefront-web-bff.uri}")
    private String storefrontWebBffUri;

    @Value("${apigateway.routes.seller-web-bff.uri}")
    private String sellerWebBffUri;

    @Value("${apigateway.routes.ops-web-bff.uri}")
    private String opsWebBffUri;

    @Bean
    public RouteLocator gateway(RouteLocatorBuilder builder, GatewayProperties properties) {
        String auth = properties.hosts().auth();
        String shop = properties.hosts().shop();
        String seller = properties.hosts().seller();
        String ops = properties.hosts().ops();
        return builder.routes()
                .route("auth", r -> r.order(0)
                        .predicate(exchange -> HostMatcher.matches(exchange, auth))
                        .filters(f -> f.dedupeResponseHeader(DEDUPE_CORS, "RETAIN_FIRST"))
                        .uri(oauth2ServiceUri))
                .route("storefront-web-bff", r -> r.order(0)
                        .predicate(exchange -> HostMatcher.matches(exchange, shop)).and().path(BFF_PATHS)
                        .filters(f -> f.dedupeResponseHeader(DEDUPE_CORS, "RETAIN_FIRST"))
                        .uri(storefrontWebBffUri))
                .route(FRONTEND_ROUTE_ID, r -> r.order(1)
                        .predicate(exchange -> HostMatcher.matches(exchange, shop))
                        .filters(f -> f.dedupeResponseHeader(DEDUPE_CORS, "RETAIN_FIRST"))
                        .uri(properties.frontends().shop()))
                .route("seller-web-bff", r -> r.order(0)
                        .predicate(exchange -> HostMatcher.matches(exchange, seller)).and().path(BFF_PATHS)
                        .filters(f -> f.dedupeResponseHeader(DEDUPE_CORS, "RETAIN_FIRST"))
                        .uri(sellerWebBffUri))
                .route("seller-frontend", r -> r.order(1)
                        .predicate(exchange -> HostMatcher.matches(exchange, seller))
                        .filters(f -> f.dedupeResponseHeader(DEDUPE_CORS, "RETAIN_FIRST"))
                        .uri(properties.frontends().seller()))
                .route("ops-web-bff", r -> r.order(0)
                        .predicate(exchange -> HostMatcher.matches(exchange, ops)).and().path(BFF_PATHS)
                        .filters(f -> f.dedupeResponseHeader(DEDUPE_CORS, "RETAIN_FIRST"))
                        .uri(opsWebBffUri))
                .route("ops-frontend", r -> r.order(1)
                        .predicate(exchange -> HostMatcher.matches(exchange, ops))
                        .filters(f -> f.dedupeResponseHeader(DEDUPE_CORS, "RETAIN_FIRST"))
                        .uri(properties.frontends().ops()))
                .build();
    }
}
