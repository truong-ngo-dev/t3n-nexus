package vn.t3nexus.oauth2.infrastructure.security.oauth2;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import vn.t3nexus.oauth2.infrastructure.security.AuthenticationEntryPoints;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.security.authentication.ott.OneTimeTokenAuthentication;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.oauth2.server.authorization.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.jackson.SecurityJacksonModules;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import vn.t3nexus.oauth2.infrastructure.security.oauth2.store.LoginRequestDetailsMixin;
import vn.t3nexus.oauth2.infrastructure.security.extension.authentication.UserAccountDetails;
import org.springframework.security.oauth2.server.authorization.*;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationProvider;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.transaction.support.TransactionOperations;
import vn.t3nexus.oauth2.application.login_session.record_client_session_issued.RecordClientSessionIssued;
import vn.t3nexus.oauth2.application.login_session.resolve_authorization_session.ResolveAuthorizationSession;
import vn.t3nexus.oauth2.domain.login_session.LoginSessionRepository;
import vn.t3nexus.oauth2.infrastructure.security.mfa.MfaEnforcementFilter;
import tools.jackson.databind.json.JsonMapper;
import vn.t3nexus.oauth2.infrastructure.security.extension.authentication.LoginRequestDetails;
import vn.t3nexus.oauth2.infrastructure.security.extension.session.IdTokenHintVerifier;
import vn.t3nexus.oauth2.infrastructure.security.extension.session.IssuedTokenListener;
import vn.t3nexus.oauth2.infrastructure.security.extension.session.SidAuthorizationLookup;

import vn.t3nexus.oauth2.infrastructure.security.extension.session.SessionBoundAuthorizationService;

import vn.t3nexus.oauth2.infrastructure.security.oauth2.validator.StateRequiredValidator;
import vn.t3nexus.oauth2.infrastructure.security.oauth2.store.UserAccountDetailsMixin;
import vn.t3nexus.oauth2.infrastructure.security.oauth2.store.OneTimeTokenAuthenticationMixin;
import vn.t3nexus.oauth2.infrastructure.security.extension.session.AuthorizationSessionGateFilter;
import vn.t3nexus.oauth2.infrastructure.security.extension.session.AuthorizationSessionSource;
import vn.t3nexus.oauth2.infrastructure.security.extension.session.RequestedSidSource;
import vn.t3nexus.oauth2.infrastructure.security.extension.logging.UserIdLogContextFilter;
import vn.t3nexus.oauth2.infrastructure.security.extension.logout.OidcLogoutFailureHandler;
import vn.t3nexus.oauth2.infrastructure.security.extension.logout.OidcLogoutSuccessHandler;

@Configuration
@RequiredArgsConstructor
public class OAuth2AuthorizationServerConfig {

    @Bean
    @Order(1)
    public SecurityFilterChain authorizationServerSecurityFilterChain(
            HttpSecurity http,
            OidcLogoutSuccessHandler oidcLogoutHandler,
            OidcLogoutFailureHandler oidcLogoutFailureHandler,
            ResolveAuthorizationSession resolveAuthorizationSession,
            RegisteredClientRepository registeredClientRepository) {
        OAuth2AuthorizationServerConfigurer authorizationServerConfigurer = new OAuth2AuthorizationServerConfigurer();

        http
                .securityMatcher(authorizationServerConfigurer.getEndpointsMatcher())
                .cors(Customizer.withDefaults())
                .with(authorizationServerConfigurer, configurer -> configurer
                        // KT-30: bắt buộc state ở yêu cầu cấp mã
                        .authorizationEndpoint(endpoint -> endpoint.authenticationProviders(providers -> providers.forEach(provider -> {
                            if (provider instanceof OAuth2AuthorizationCodeRequestAuthenticationProvider codeProvider) {
                                codeProvider.setAuthenticationValidator(StateRequiredValidator.withDefaults());
                            }
                        })))
                        .oidc(oidcConfigurer -> oidcConfigurer
                                .userInfoEndpoint(userInfo -> userInfo.userInfoMapper(new SessionUserInfoMapper()))
                                .logoutEndpoint(leConf -> leConf
                                        .logoutResponseHandler(oidcLogoutHandler)
                                        .errorResponseHandler(oidcLogoutFailureHandler))))
                // Cửa kiểm hạn của /oauth2/authorize (ADR-0003 mục 2): đứng trước AuthorizationFilter (và trước bộ lọc
                // endpoint cấp mã của Spring, được thêm sau AuthorizationFilter) để phiên đã chết bị đưa về trang đăng nhập.
                .addFilterBefore(new AuthorizationSessionGateFilter(resolveAuthorizationSession, registeredClientRepository), AuthorizationFilter.class)
                .addFilterBefore(new MfaEnforcementFilter(), AuthorizationFilter.class)
                .addFilterAfter(new UserIdLogContextFilter(), AuthorizationFilter.class)
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .anonymous(AbstractHttpConfigurer::disable)
                .exceptionHandling(ex -> ex.authenticationEntryPoint(AuthenticationEntryPoints.browserAndProtocol()));

        return http.build();
    }

    @Bean
    public OAuth2AuthorizationService auth2AuthorizationService(
            JdbcOperations jdbcOperations,
            RegisteredClientRepository repository,
            RecordClientSessionIssued recordClientSessionIssued,
            LoginSessionRepository loginSessionRepository,
            TransactionOperations transactionOperations,
            IdTokenHintVerifier idTokenHintVerifier,
            RequestedSidSource requestedSidSource,
            AuthorizationSessionSource authorizationSessionSource,
            ObjectProvider<IssuedTokenListener> issuedTokenListeners
    ) {
        JdbcOAuth2AuthorizationService jdbcService = new JdbcOAuth2AuthorizationService(jdbcOperations, repository);
        ClassLoader classLoader = JdbcOAuth2AuthorizationService.class.getClassLoader();

        BasicPolymorphicTypeValidator.Builder validatorBuilder = BasicPolymorphicTypeValidator.builder()
                .allowIfSubType(OneTimeTokenAuthentication.class)
                .allowIfSubType(UserAccountDetails.class)
                .allowIfSubType(LoginRequestDetails.class);

        JsonMapper jsonMapper = JsonMapper.builder()
                .findAndAddModules()
                .addModules(SecurityJacksonModules.getModules(classLoader, validatorBuilder))
                .addMixIn(LoginRequestDetails.class, LoginRequestDetailsMixin.class)
                .addMixIn(OneTimeTokenAuthentication.class, OneTimeTokenAuthenticationMixin.class)
                .addMixIn(UserAccountDetails.class, UserAccountDetailsMixin.class)
                .build();
        JdbcOAuth2AuthorizationService.JsonMapperOAuth2AuthorizationRowMapper rowMapper =
                new JdbcOAuth2AuthorizationService.JsonMapperOAuth2AuthorizationRowMapper(repository, jsonMapper);
        jdbcService.setAuthorizationRowMapper(rowMapper);

        SidAuthorizationLookup lookup = new SidAuthorizationLookup(jdbcService, loginSessionRepository,
                requestedSidSource, idTokenHintVerifier);
        return new SessionBoundAuthorizationService(jdbcService, recordClientSessionIssued,
                authorizationSessionSource, transactionOperations, lookup,
                issuedTokenListeners.orderedStream().toList());
    }

    @Bean
    public OAuth2AuthorizationConsentService oAuth2AuthorizationConsentService(
            JdbcOperations jdbcOperations, RegisteredClientRepository repository) {
        return new JdbcOAuth2AuthorizationConsentService(jdbcOperations, repository);
    }

}
