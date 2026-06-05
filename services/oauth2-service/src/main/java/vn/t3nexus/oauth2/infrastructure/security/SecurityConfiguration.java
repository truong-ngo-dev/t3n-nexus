package vn.t3nexus.oauth2.infrastructure.security;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
// TODO [mfa]: import org.springframework.security.???. EnableMultiFactorAuthentication — verify package khi build lần đầu
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import vn.t3nexus.oauth2.infrastructure.security.handler.DeviceAwareAuthenticationFailureHandler;
import vn.t3nexus.oauth2.infrastructure.security.handler.DeviceAwareAuthenticationSuccessHandler;
import vn.t3nexus.oauth2.infrastructure.security.mfa.EmailOtpGenerationSuccessHandler;
import vn.t3nexus.oauth2.infrastructure.security.mfa.EmailOtpOneTimeTokenService;
import vn.t3nexus.oauth2.infrastructure.security.model.DeviceAwareWebAuthenticationDetails;
import vn.t3nexus.oauth2.infrastructure.security.oauth2.DeviceAwareAuthorizationRequestResolver;
import vn.t3nexus.oauth2.infrastructure.security.service.SocialLoginOidcUserService;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
// TODO [mfa]: @EnableMultiFactorAuthentication(authorities = {}) — cần để Spring thêm FACTOR_OTT sau OTT auth
@RequiredArgsConstructor
public class SecurityConfiguration {

    private final ApplicationContext               applicationContext;
    private final EmailOtpOneTimeTokenService      emailOtpOneTimeTokenService;
    private final EmailOtpGenerationSuccessHandler emailOtpGenerationSuccessHandler;
    private final SocialLoginOidcUserService       socialLoginOidcUserService;
    private final ClientRegistrationRepository     clientRegistrationRepository;

    @Value("${app.cors.allowed-origins:http://localhost:4200}")
    private String allowedOrigins;

    @Value("${spring.security.oauth2.authorizationserver.issuer}")
    private String issuerUri;

    /**
     * Resource server filter chain — bảo vệ /api/** bằng Bearer JWT.
     * Order(2) cao hơn defaultSecurityFilterChain(Order(3)) để match trước.
     */
    @Bean
    @Order(2)
    public SecurityFilterChain apiResourceServerFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/api/**")
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt ->
                        jwt.jwkSetUri(issuerUri + "/oauth2/jwks")));
        return http.build();
    }

    @Bean
    @Order(3)
    public SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .anonymous(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/login/device-hint").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex.defaultAuthenticationEntryPointFor(
                        new LoginUrlAuthenticationEntryPoint("/login"),
                        new MediaTypeRequestMatcher(MediaType.TEXT_HTML)))
                .formLogin(conf -> conf
                        .loginPage("/login")
                        .permitAll()
                        .authenticationDetailsSource(DeviceAwareWebAuthenticationDetails::new)
                        .successHandler(deviceAwareAuthenticationSuccessHandler())
                        .failureHandler(deviceAwareAuthenticationFailureHandler()))
                .oneTimeTokenLogin(ott -> ott
                        .loginPage("/login")
                        .loginProcessingUrl("/login/ott")
                        .tokenService(emailOtpOneTimeTokenService)
                        .tokenGenerationSuccessHandler(emailOtpGenerationSuccessHandler));

        http.oauth2Login(oauth2 -> oauth2
                .loginPage("/login")
                .authorizationEndpoint(endpoint -> endpoint
                        .authorizationRequestResolver(
                                new DeviceAwareAuthorizationRequestResolver(clientRegistrationRepository)))
                .userInfoEndpoint(userInfo -> userInfo.oidcUserService(socialLoginOidcUserService))
                .successHandler(deviceAwareAuthenticationSuccessHandler()));

        return http.build();
    }

    public AuthenticationSuccessHandler deviceAwareAuthenticationSuccessHandler() {
        return applicationContext.getBean(DeviceAwareAuthenticationSuccessHandler.class);
    }

    public AuthenticationFailureHandler deviceAwareAuthenticationFailureHandler() {
        return applicationContext.getBean(DeviceAwareAuthenticationFailureHandler.class);
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.asList(allowedOrigins.split(",")));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowedMethods(List.of("*"));
        configuration.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }
}
