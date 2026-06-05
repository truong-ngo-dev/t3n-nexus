package vn.t3nexus.webgateway.infrastructure.configuration.security;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.security.web.server.authentication.HttpStatusServerEntryPoint;
import org.springframework.security.web.server.authentication.RedirectServerAuthenticationEntryPoint;
import org.springframework.security.web.server.authentication.logout.ServerLogoutSuccessHandler;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers;

@Configuration
@EnableWebFluxSecurity
@RequiredArgsConstructor
public class SecurityConfiguration {

    private final ReactiveClientRegistrationRepository       clientRegistrationRepository;
    private final SessionMappingAuthenticationSuccessHandler sessionMappingSuccessHandler;
    private final ReactiveStringRedisTemplate                redisTemplate;

    @Value("${app.logout.post-redirect-uri}")
    private String postLogoutRedirectUri;

    @Value("${app.logout.end-session-uri}")
    private String endSessionUri;

    /**
     * Chain 1: /webgw/internal/** — internal back-channel endpoints (e.g. session revocation).
     * TODO: switch to .hasAuthority("SCOPE_webgw.internal") + resource server after testing.
     */
    @Bean
    @Order(1)
    public SecurityWebFilterChain internalFilterChain(ServerHttpSecurity http) {
        return http
                .securityMatcher(ServerWebExchangeMatchers.pathMatchers("/webgw/internal/**"))
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(ex -> ex.anyExchange().permitAll())
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
                        .pathMatchers(HttpMethod.POST, "/api/oauth2/v1/users/register").permitAll()
                        .pathMatchers("/api/**").authenticated()
                        .anyExchange().permitAll())
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .oauth2Login(oauth2 -> oauth2
                        .authenticationSuccessHandler(sessionMappingSuccessHandler))
                .exceptionHandling(ex -> ex.authenticationEntryPoint(authenticationEntryPoint()))
                .logout(spec -> spec
                        .logoutUrl("/webgw/auth/logout")
                        .logoutSuccessHandler(logoutSuccessHandler()))
                .build();
    }

    @Bean
    public ServerAuthenticationEntryPoint authenticationEntryPoint() {
        return (exchange, e) -> {
            MediaType accept = exchange.getRequest().getHeaders().getAccept()
                    .stream().findFirst().orElse(MediaType.ALL);

            if (accept.includes(MediaType.TEXT_HTML)) {
                return new RedirectServerAuthenticationEntryPoint("/oauth2/authorization/web-gateway").commence(exchange, e);
            }
            return new HttpStatusServerEntryPoint(HttpStatus.UNAUTHORIZED).commence(exchange, e);
        };
    }

    private ServerLogoutSuccessHandler logoutSuccessHandler() {
        WebGatewayLogoutSuccessHandler handler = new WebGatewayLogoutSuccessHandler(
                clientRegistrationRepository, redisTemplate);
        handler.setPostLogoutRedirectUri(postLogoutRedirectUri);
        handler.setEndSessionUri(endSessionUri);
        return handler;
    }
}
