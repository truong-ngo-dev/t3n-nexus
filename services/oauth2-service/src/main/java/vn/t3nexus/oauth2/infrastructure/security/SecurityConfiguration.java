package vn.t3nexus.oauth2.infrastructure.security;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.NegatedRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import vn.t3nexus.oauth2.infrastructure.security.federated.DeviceAwareAuthenticationSuccessHandler;
import vn.t3nexus.oauth2.infrastructure.security.mfa.OttAuthenticationFailureHandler;
import vn.t3nexus.oauth2.infrastructure.security.federated.SocialLoginFailureHandler;
import vn.t3nexus.oauth2.infrastructure.security.mfa.EmailOtpGenerationSuccessHandler;
import vn.t3nexus.oauth2.infrastructure.security.mfa.EmailOtpOneTimeTokenService;
import vn.t3nexus.oauth2.application.login_session.open_login_session.OpenLoginSession;
import vn.t3nexus.oauth2.domain.login_session.DeviceKeyHasher;
import vn.t3nexus.oauth2.infrastructure.security.extension.authentication.LoginSessionAuthenticationSuccessHandler;
import vn.t3nexus.oauth2.infrastructure.security.extension.logging.UserIdLogContextFilter;
import vn.t3nexus.oauth2.infrastructure.security.session.DeviceKeyCookieIssuer;
import vn.t3nexus.oauth2.infrastructure.security.extension.authentication.PasswordLoginFailureHandler;
import vn.t3nexus.oauth2.infrastructure.security.extension.authentication.LoginRequestDetails;
import vn.t3nexus.oauth2.infrastructure.security.federated.DeviceAwareAuthorizationRequestResolver;
import vn.t3nexus.oauth2.infrastructure.security.federated.SocialLoginOidcUserService;
import vn.t3nexus.lib.ratelimiter.RateLimiter;

import java.util.Arrays;
import java.util.List;
import vn.t3nexus.oauth2.infrastructure.security.extension.authentication.LoginRateLimitFilter;
import vn.t3nexus.oauth2.infrastructure.security.extension.authentication.LoginRateLimitProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties(LoginRateLimitProperties.class)
// @EnableMultiFactorAuthentication(authorities = {}) — không sử dụng do bị giới hạn cứng factor tham gia vào chain
@RequiredArgsConstructor
public class SecurityConfiguration {

    private final ApplicationContext               applicationContext;
    private final EmailOtpOneTimeTokenService      emailOtpOneTimeTokenService;
    private final EmailOtpGenerationSuccessHandler emailOtpGenerationSuccessHandler;
    private final OttAuthenticationFailureHandler  ottAuthenticationFailureHandler;
    private final SocialLoginOidcUserService       socialLoginOidcUserService;
    private final ClientRegistrationRepository     clientRegistrationRepository;
    private final RateLimiter                      rateLimiter;
    private final LoginRateLimitProperties         loginRateLimitProperties;
    private final OpenLoginSession                 openLoginSession;
    private final DeviceKeyCookieIssuer            deviceKeyCookieIssuer;
    private final DeviceKeyHasher                  deviceKeyHasher;
    private final java.time.Clock                  clock;

    @Value("${app.cors.allowed-origins:http://*.localhost:*}")
    private String allowedOrigins;

    @Value("${server.servlet.session.cookie.secure:true}")
    private boolean secureCookies;

    /**
     * Resource server filter chain — bảo vệ /api/** (API của chính máy chủ xác thực) bằng Bearer JWT do chính nó cấp.
     * Order(2) cao hơn defaultSecurityFilterChain(Order(3)) để match trước.
     */
    @Bean
    @Order(2)
    public SecurityFilterChain apiResourceServerFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/api/**")
                // Chỉ nhận Bearer: không đọc SecurityContext từ session. Nếu không, cookie đăng nhập của trình duyệt cũng qua được chuỗi này
                // mà CSRF lại tắt (cookie tự gắn nên bị lợi dụng chéo trang).
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .addFilterAfter(new UserIdLogContextFilter(), AuthorizationFilter.class)
                // Kiểm chữ ký bằng bộ khóa công khai ngay trong dịch vụ (bean JwtDecoder dựng từ JWKSource), không gọi lại chính mình qua HTTP.
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));
        return http.build();
    }

    @Bean
    @Order(3)
    public SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http) throws Exception {
        HttpSessionRequestCache requestCache = new HttpSessionRequestCache();
        requestCache.setRequestMatcher(new NegatedRequestMatcher(new OrRequestMatcher(
                PathPatternRequestMatcher.withDefaults().matcher("/login"),
                PathPatternRequestMatcher.withDefaults().matcher("/login/**")
        )));

        http
                // KT-29: chuỗi dùng cookie phải có CSRF (ADR-0005); /register là API JSON do BFF gọi, không mang cookie phiên.
                // Mã CSRF nằm trong cookie riêng sống 30 ngày và không xoay khi đăng nhập, để session hết hạn hay nhấn Back không làm
                // biểu mẫu đang mở mất hiệu lực (trong suốt với người dùng); trang đăng nhập tự cấp lại cookie khi chưa có.
                .csrf(csrf -> csrf
                        .csrfTokenRepository(loginCsrfTokenRepository())
                        .sessionAuthenticationStrategy((authentication, request, response) -> { })
                        .ignoringRequestMatchers(PathPatternRequestMatcher.withDefaults().matcher("/register")))
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .anonymous(AbstractHttpConfigurer::disable)
                .requestCache(rc -> rc.requestCache(requestCache))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(new OrRequestMatcher(
                                PathPatternRequestMatcher.withDefaults().matcher("/login"),
                                PathPatternRequestMatcher.withDefaults().matcher("/login/**"),
                                PathPatternRequestMatcher.withDefaults().matcher("/password/setup"),
                                PathPatternRequestMatcher.withDefaults().matcher("/password/setup/success"),
                                PathPatternRequestMatcher.withDefaults().matcher("/register")
                        )).permitAll()
                        // sendError (vd 403 của CSRF) chuyển sang /error: không cho phép thì bị đẩy về /login thay vì trả đúng mã lỗi
                        .dispatcherTypeMatchers(jakarta.servlet.DispatcherType.ERROR).permitAll()
                        .anyRequest().authenticated())
                .addFilterAfter(new UserIdLogContextFilter(), AuthorizationFilter.class)
                .exceptionHandling(ex -> ex.authenticationEntryPoint(AuthenticationEntryPoints.browserAndProtocol()))
                .addFilterBefore(new LoginRateLimitFilter(rateLimiter, loginRateLimitProperties), UsernamePasswordAuthenticationFilter.class)
                .formLogin(conf -> conf
                        .loginPage("/login")
                        .permitAll()
                        .authenticationDetailsSource(request -> new LoginRequestDetails(request, deviceKeyHasher))
                        .successHandler(new LoginSessionAuthenticationSuccessHandler(openLoginSession, deviceKeyCookieIssuer, clock))
                        .failureHandler(new PasswordLoginFailureHandler()))
                .oneTimeTokenLogin(ott -> ott
                        .loginPage("/login")
                        .loginProcessingUrl("/login/ott")
                        .tokenService(emailOtpOneTimeTokenService)
                        .tokenGenerationSuccessHandler(emailOtpGenerationSuccessHandler)
                        .failureHandler(ottAuthenticationFailureHandler))
                .oauth2Login(oauth2 -> oauth2
                        .loginPage("/login")
                        .authorizationEndpoint(endpoint -> endpoint
                                .authorizationRequestResolver(new DeviceAwareAuthorizationRequestResolver(clientRegistrationRepository)))
                        .userInfoEndpoint(userInfo -> userInfo.oidcUserService(socialLoginOidcUserService))
                        .successHandler(deviceAwareAuthenticationSuccessHandler())
                        .failureHandler(socialLoginFailureHandler()));

        return http.build();
    }

    private CsrfTokenRepository loginCsrfTokenRepository() {
        CookieCsrfTokenRepository repository = new CookieCsrfTokenRepository();   // HttpOnly: biểu mẫu do máy chủ dựng, không cần JavaScript đọc
        repository.setCookieCustomizer(cookie -> cookie
                .secure(secureCookies)
                .sameSite("Lax")
                .maxAge(java.time.Duration.ofDays(30)));
        return repository;
    }

    public AuthenticationSuccessHandler deviceAwareAuthenticationSuccessHandler() {
        return applicationContext.getBean(DeviceAwareAuthenticationSuccessHandler.class);
    }

    public AuthenticationFailureHandler socialLoginFailureHandler() {
        return applicationContext.getBean(SocialLoginFailureHandler.class);
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(Arrays.stream(allowedOrigins.split(",")).map(String::strip).filter(s -> !s.isEmpty()).toList());
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
