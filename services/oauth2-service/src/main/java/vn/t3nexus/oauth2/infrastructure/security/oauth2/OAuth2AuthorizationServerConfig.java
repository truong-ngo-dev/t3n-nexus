package vn.t3nexus.oauth2.infrastructure.security.oauth2;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.security.authentication.ott.OneTimeTokenAuthentication;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.oauth2.server.authorization.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.core.Authentication;
import org.springframework.security.jackson.SecurityJacksonModules;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import vn.t3nexus.oauth2.infrastructure.security.service.UserCredentialDetails;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.server.authorization.*;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.oidc.authentication.OidcLogoutAuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.server.authorization.oidc.authentication.OidcUserInfoAuthenticationContext;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;
import vn.t3nexus.oauth2.infrastructure.security.mfa.MfaEnforcementFilter;
import org.springframework.util.StringUtils;
import tools.jackson.databind.json.JsonMapper;
import vn.t3nexus.oauth2.infrastructure.security.handler.AuthorizationRevokingLogoutSuccessHandler;
import vn.t3nexus.oauth2.infrastructure.security.model.DeviceAwareWebAuthenticationDetails;

@Configuration
@RequiredArgsConstructor
public class OAuth2AuthorizationServerConfig {

    @Bean
    @Order(1)
    public SecurityFilterChain authorizationServerSecurityFilterChain(HttpSecurity http) throws Exception {
        OAuth2AuthorizationServerConfigurer authorizationServerConfigurer = new OAuth2AuthorizationServerConfigurer();

        http
                .securityMatcher(authorizationServerConfigurer.getEndpointsMatcher())
                .with(authorizationServerConfigurer, configurer -> configurer
                        .oidc(oidcConfigurer -> oidcConfigurer
                                .userInfoEndpoint(userInfo -> userInfo.userInfoMapper(this::mapUserInfo))
                                .logoutEndpoint(logoutEndpointConfigurer ->
                                        logoutEndpointConfigurer.logoutResponseHandler(logoutSuccessHandler()))))
                .addFilterBefore(new MfaEnforcementFilter(), AuthorizationFilter.class)
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .anonymous(AbstractHttpConfigurer::disable)
                .exceptionHandling(ex -> ex.defaultAuthenticationEntryPointFor(
                        new LoginUrlAuthenticationEntryPoint("/login"),
                        new MediaTypeRequestMatcher(MediaType.TEXT_HTML)));

        return http.build();
    }

    @Bean
    public OAuth2AuthorizationService auth2AuthorizationService(
            JdbcOperations jdbcOperations,
            RegisteredClientRepository repository
            // TODO [business]: CompleteLogin completeLogin
    ) {
        JdbcOAuth2AuthorizationService jdbcService = new JdbcOAuth2AuthorizationService(jdbcOperations, repository);
        ClassLoader classLoader = JdbcOAuth2AuthorizationService.class.getClassLoader();

        BasicPolymorphicTypeValidator.Builder validatorBuilder = BasicPolymorphicTypeValidator.builder()
                .allowIfSubType(OneTimeTokenAuthentication.class)
                .allowIfSubType(UserCredentialDetails.class);

        JsonMapper jsonMapper = JsonMapper.builder()
                .findAndAddModules()
                .addModules(SecurityJacksonModules.getModules(classLoader, validatorBuilder))
                .addMixIn(DeviceAwareWebAuthenticationDetails.class, DeviceAwareWebAuthenticationDetailsMixin.class)
                .addMixIn(OneTimeTokenAuthentication.class, OneTimeTokenAuthenticationMixin.class)
                .addMixIn(UserCredentialDetails.class, UserCredentialDetailsMixin.class)
                .build();
        JdbcOAuth2AuthorizationService.JsonMapperOAuth2AuthorizationRowMapper rowMapper =
                new JdbcOAuth2AuthorizationService.JsonMapperOAuth2AuthorizationRowMapper(repository, jsonMapper);
        jdbcService.setAuthorizationRowMapper(rowMapper);
        // TODO [business]: wrap với AuditingOAuth2AuthorizationService(jdbcService, completeLogin) khi CompleteLogin sẵn sàng
        return new AuditingOAuth2AuthorizationService(jdbcService);
    }

    @Bean
    public OAuth2AuthorizationConsentService oAuth2AuthorizationConsentService(
            JdbcOperations jdbcOperations, RegisteredClientRepository repository) {
        return new JdbcOAuth2AuthorizationConsentService(jdbcOperations, repository);
    }

    /**
     * Expose all access token JWT claims (including custom ones) through the OIDC UserInfo endpoint.
     */
    private OidcUserInfo mapUserInfo(OidcUserInfoAuthenticationContext context) {
        OAuth2Authorization authorization = context.getAuthorization();
        OAuth2Authorization.Token<OAuth2AccessToken> accessToken = authorization.getToken(OAuth2AccessToken.class);
        if (accessToken == null || accessToken.getClaims() == null) {
            throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_TOKEN);
        }
        return new OidcUserInfo(accessToken.getClaims());
    }

    @Bean
    public AuthenticationSuccessHandler logoutSuccessHandler() {
        return (request, response, authentication) -> {
            // TODO [business]: RevokeSession revokeSession = context.getBean(RevokeSession.class);
            OidcLogoutAuthenticationToken oidcLogoutAuthentication = (OidcLogoutAuthenticationToken) authentication;
            LogoutHandler logoutHandler = new SecurityContextLogoutHandler();
            // TODO [business]: inject RevokeSession vào AuthorizationRevokingLogoutSuccessHandler
            LogoutSuccessHandler logoutSuccessHandler = new AuthorizationRevokingLogoutSuccessHandler();

            if (oidcLogoutAuthentication.isPrincipalAuthenticated()
                    && StringUtils.hasText(oidcLogoutAuthentication.getSessionId())) {
                logoutHandler.logout(request, response, (Authentication) oidcLogoutAuthentication.getPrincipal());
            }
            logoutSuccessHandler.onLogoutSuccess(request, response, oidcLogoutAuthentication);
        };
    }
}
