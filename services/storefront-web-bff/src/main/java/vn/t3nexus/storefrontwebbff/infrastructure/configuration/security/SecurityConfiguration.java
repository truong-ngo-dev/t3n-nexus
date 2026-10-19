package vn.t3nexus.storefrontwebbff.infrastructure.configuration.security;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.security.web.server.authentication.logout.ServerLogoutSuccessHandler;
import org.springframework.security.web.server.authentication.logout.WebSessionServerLogoutHandler;
import org.springframework.security.web.server.csrf.CsrfToken;
import org.springframework.security.web.server.csrf.ServerCsrfTokenRepository;
import org.springframework.security.web.server.csrf.ServerCsrfTokenRequestAttributeHandler;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers;
import org.springframework.web.server.WebFilter;
import reactor.core.publisher.Mono;
import vn.t3nexus.storefrontwebbff.infrastructure.web.SecurityErrorHandlers;

@Configuration
@EnableWebFluxSecurity
@RequiredArgsConstructor
public class SecurityConfiguration {

    private final ReactiveClientRegistrationRepository       clientRegistrationRepository;
    private final SessionMappingAuthenticationSuccessHandler sessionMappingSuccessHandler;
    private final SessionMappingStore                       mappings;
    private final ServerCsrfTokenRepository                 csrfTokenRepository;

    @Value("${app.logout.post-redirect-uri}")
    private String postLogoutRedirectUri;

    @Value("${app.logout.end-session-uri}")
    private String endSessionUri;

    /*
     * Chain 1: /bff/internal/** — internal back-channel endpoints (e.g. session revocation).
     * Yêu cầu JWT service-account với scope "storefront.internal" — token do oauth2-service tự mint
     * qua client_credentials grant (client "oauth2-service-internal", xem
     * StorefrontWebBffRevocationClient + oauth2-service migration V10), verify qua JWKS
     * (spring.security.oauth2.resourceserver.jwt.jwk-set-uri ở dưới).
     */


    /**
     * Bộ giải mã token dịch vụ cho chuỗi nội bộ: cùng khóa công khai của Xác thực, nhưng độ lệch đồng hồ chỉ 5 giây
     * ({@code app.security.jwt.clock-skew}) thay cho 60 giây mặc định, như Xác thực (R18).
     */
    @Bean
    public ReactiveJwtDecoder serviceTokenDecoder(
            @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}") String jwkSetUri,
            @Value("${app.security.jwt.clock-skew:5s}") Duration clockSkew) {
        NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder.withJwkSetUri(jwkSetUri).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(new JwtTimestampValidator(clockSkew)));
        return decoder;
    }

    @Bean
    @Order(1)
    public SecurityWebFilterChain internalFilterChain(ServerHttpSecurity http) {
        return http
                .securityMatcher(ServerWebExchangeMatchers.pathMatchers("/bff/internal/**"))
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(ex -> ex.anyExchange().hasAuthority("SCOPE_storefront.internal"))
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
                .build();
    }

    /**
     * Chain 2: everything else — OAuth2 login client + session management.
     */
    @Bean
    @Order(2)
    public SecurityWebFilterChain securityFilterChain(ServerHttpSecurity http) {
        return http
                .headers(conf -> conf.frameOptions(ServerHttpSecurity.HeaderSpec.FrameOptionsSpec::disable))
                .authorizeExchange(exchange -> exchange
                        // Register không còn qua storefront-web-bff — bypass thẳng api-gateway → oauth2-service
                        // (host auth, POST /register, xem api-gateway RouteConfiguration + ADR-0006).
                        // Verify email / resend-verification: user chưa từng login (account PENDING),
                        // phải permitAll riêng, không thể rơi vào .authenticated() chung bên dưới.
                        .pathMatchers(HttpMethod.GET, "/api/identity/users/verify").permitAll()
                        .pathMatchers(HttpMethod.POST, "/api/identity/users/resend-verification").permitAll()
                        // catalog-service — Guest/Customer browse không cần login (category tree, brand
                        // list, product/variant detail). Publish/write endpoint (/api/catalog/seller/**,
                        // /api/catalog/admin/**) vẫn rơi vào .authenticated() chung bên dưới vì không match
                        // các pattern hẹp này.
                        .pathMatchers(HttpMethod.GET,
                                "/api/catalog/categories",
                                "/api/catalog/categories/*/attributes",
                                "/api/catalog/brands",
                                "/api/catalog/products/*",
                                "/api/catalog/products/*/variants").permitAll()
                        .pathMatchers("/api/**").authenticated()
                        .anyExchange().permitAll())
                // CSRF gửi hai lần (cookie đọc được + tiêu đề X-XSRF-TOKEN, tech.md T4): mã thô, không XOR, vì JavaScript của SPA gửi lại nguyên giá trị cookie
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokenRepository)
                        .csrfTokenRequestHandler(new ServerCsrfTokenRequestAttributeHandler())
                        .accessDeniedHandler(SecurityErrorHandlers.csrfDenied()))
                .addFilterAfter(csrfCookieFilter(), SecurityWebFiltersOrder.CSRF)
                .oauth2Login(oauth2 -> oauth2
                        .authenticationSuccessHandler(sessionMappingSuccessHandler))
                .exceptionHandling(ex -> ex.authenticationEntryPoint(authenticationEntryPoint()).accessDeniedHandler(SecurityErrorHandlers.forbidden()))
                .logout(spec -> spec
                        .logoutUrl("/bff/auth/logout")
                        // Mặc định WebFlux chỉ xóa ngữ cảnh bảo mật (SecurityContextServerLogoutHandler), KHÔNG xóa phiên: phải thêm bộ xóa phiên chuẩn
                        // (nếu không phiên cùng token còn trong Redis tới hết hạn; phát hiện khi chạy thật bước 11)
                        .logoutHandler(new WebSessionServerLogoutHandler())
                        // Mặc định WebFlux chỉ xóa ngữ cảnh bảo mật (SecurityContextServerLogoutHandler), KHÔNG xóa phiên: phải thêm bộ xóa phiên chuẩn
                        // (nếu không phiên cùng token còn trong Redis tới hết hạn; phát hiện khi chạy thật bước 11)
                        .logoutSuccessHandler(logoutSuccessHandler()))
                .build();
    }

    /**
     * Chưa có phiên: 401 khung lỗi chung ở mọi {@code Accept} (bảng hành vi lỗi, loại 1). Người gọi {@code /api/**} là fetch của SPA; chuyển
     * hướng sang host {@code auth} làm trình duyệt báo lỗi CORS thay vì cho SPA biết cần đăng nhập. SPA tự điều hướng tới
     * {@code /bff/auth/login} khi nhận 401. Luồng đăng nhập (điều hướng trình duyệt) vẫn là 302 của Spring OAuth2 Client.
     */
    @Bean
    public ServerAuthenticationEntryPoint authenticationEntryPoint() {
        return SecurityErrorHandlers.unauthenticated();
    }

    private ServerLogoutSuccessHandler logoutSuccessHandler() {
        StorefrontWebBffLogoutSuccessHandler handler = new StorefrontWebBffLogoutSuccessHandler(
                clientRegistrationRepository, mappings);
        handler.setPostLogoutRedirectUri(postLogoutRedirectUri);
        handler.setEndSessionUri(endSessionUri);
        return handler;
    }

    /**
     * WebFlux chỉ phát cookie {@code XSRF-TOKEN} khi mã được dùng, nên bộ lọc này buộc lấy mã ở mọi yêu cầu: {@code GET} đầu tiên luôn kèm
     * cookie, và khi cookie mất thì một {@code GET} nhẹ (vd {@code /bff/auth/session}) phát lại (`tech.md` T4).
     */
    private static WebFilter csrfCookieFilter() {
        return (exchange, chain) -> {
            Mono<CsrfToken> token = exchange.getAttribute(CsrfToken.class.getName());
            return token != null ? token.then(chain.filter(exchange)) : chain.filter(exchange);
        };
    }
}
