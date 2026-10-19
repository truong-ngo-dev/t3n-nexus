package vn.t3nexus.oauth2.infrastructure.security.oauth2.token;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.security.oauth2.server.authorization.token.DefaultOAuth2TokenContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenContext;
import vn.t3nexus.oauth2.domain.login_session.SessionLifetimePolicy;
import vn.t3nexus.oauth2.domain.login_session.SessionLifetimePolicy.Lifetimes;
import vn.t3nexus.oauth2.domain.user_account.Role;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/** KT-18, 20 (phần bộ sinh refresh token): hạn theo vai, cắt theo hạn phiên, từ chối khi phiên đã hết hạn. */
class SessionBoundRefreshTokenGeneratorTest {

    private static final Instant NOW = Instant.parse("2026-10-14T03:00:00Z");

    private static final SessionLifetimePolicy POLICY = new SessionLifetimePolicy(
            new Lifetimes(Duration.ofMinutes(15), Duration.ofDays(7), Duration.ofDays(30)),
            new Lifetimes(Duration.ofMinutes(15), Duration.ofMinutes(30), Duration.ofHours(10)));

    private static final RegisteredClient CLIENT = RegisteredClient.withId("rc-1").clientId("storefront-web")
            .clientSecret("{noop}x").authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
            .redirectUri("http://localhost/cb")
            .tokenSettings(TokenSettings.builder().refreshTokenTimeToLive(Duration.ofDays(1)).build()).build();

    private final SessionBoundRefreshTokenGenerator generator =
            new SessionBoundRefreshTokenGenerator(POLICY, Clock.fixed(NOW, ZoneOffset.UTC));

    private static OAuth2Authorization authorization(SessionBinding binding) {
        OAuth2Authorization.Builder builder = OAuth2Authorization.withRegisteredClient(CLIENT)
                .principalName("account-1").authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE);
        if (binding != null) builder.attributes(attrs -> attrs.putAll(binding.toAttributes()));
        return builder.build();
    }

    private static OAuth2TokenContext refreshContext(OAuth2Authorization authorization) {
        return DefaultOAuth2TokenContext.builder().registeredClient(CLIENT)
                .principal(new UsernamePasswordAuthenticationToken("account-1", null))
                .authorization(authorization).tokenType(OAuth2TokenType.REFRESH_TOKEN)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN).build();
    }

    private static SessionBinding binding(Role role, Instant sessionExpiresAt) {
        return new SessionBinding("login-session-1", "sid-1", sessionExpiresAt, role);
    }

    @Test
    void user_refresh_token_lives_for_its_ttl_while_the_session_is_far_from_expiry() {
        OAuth2RefreshToken token = generator.generate(refreshContext(authorization(binding(Role.USER, NOW.plus(Duration.ofDays(30))))));

        assertThat(token.getIssuedAt()).isEqualTo(NOW);
        assertThat(token.getExpiresAt()).isEqualTo(NOW.plus(Duration.ofDays(7)));
        assertThat(token.getTokenValue()).isNotBlank();
    }

    @Test
    void internal_refresh_token_uses_the_shorter_internal_ttl() {
        OAuth2RefreshToken token = generator.generate(refreshContext(authorization(binding(Role.ADMIN, NOW.plus(Duration.ofHours(10))))));

        assertThat(token.getExpiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(30)));
    }

    @Test
    void refresh_token_is_cut_to_the_session_expiry_when_the_session_ends_sooner() {
        Instant sessionExpiry = NOW.plus(Duration.ofDays(2));

        OAuth2RefreshToken token = generator.generate(refreshContext(authorization(binding(Role.USER, sessionExpiry))));

        assertThat(token.getExpiresAt()).isEqualTo(sessionExpiry);
    }

    @Test
    void a_session_that_has_expired_cannot_get_a_refresh_token() {
        Throwable thrown = catchThrowable(
                () -> generator.generate(refreshContext(authorization(binding(Role.USER, NOW)))));

        assertThat(thrown).isInstanceOfSatisfying(OAuth2AuthenticationException.class,
                e -> assertThat(e.getError().getErrorCode()).isEqualTo(OAuth2ErrorCodes.INVALID_GRANT));
    }

    @Test
    void every_token_has_a_different_random_value() {
        OAuth2TokenContext ctx = refreshContext(authorization(binding(Role.USER, NOW.plus(Duration.ofDays(30)))));

        assertThat(generator.generate(ctx).getTokenValue()).isNotEqualTo(generator.generate(ctx).getTokenValue());
    }

    @Test
    void the_token_value_carries_no_internal_identifier() {
        OAuth2RefreshToken token = generator.generate(refreshContext(authorization(binding(Role.USER, NOW.plus(Duration.ofDays(30))))));

        assertThat(token.getTokenValue()).doesNotContain("sid-1").doesNotContain("login-session-1").doesNotContain(".");
    }

    @Test
    void a_flow_outside_a_user_session_keeps_the_ttl_of_the_client() {
        OAuth2RefreshToken token = generator.generate(refreshContext(authorization(null)));

        assertThat(token.getExpiresAt()).isEqualTo(NOW.plus(Duration.ofDays(1)));
    }

    @Test
    void only_refresh_tokens_are_generated() {
        OAuth2TokenContext accessContext = DefaultOAuth2TokenContext.builder().registeredClient(CLIENT)
                .principal(new UsernamePasswordAuthenticationToken("account-1", null))
                .authorization(authorization(binding(Role.USER, NOW.plus(Duration.ofDays(30)))))
                .tokenType(OAuth2TokenType.ACCESS_TOKEN)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE).build();

        assertThat(generator.generate(accessContext)).isNull();
    }

    @Test
    void a_public_client_using_the_authorization_code_grant_gets_no_refresh_token() {
        RegisteredClient publicClient = RegisteredClient.withId("rc-2").clientId("spa")
                .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("http://localhost/cb").build();
        OAuth2ClientAuthenticationToken clientPrincipal =
                new OAuth2ClientAuthenticationToken(publicClient, ClientAuthenticationMethod.NONE, null);
        OAuth2AuthorizationCodeAuthenticationToken grant = new OAuth2AuthorizationCodeAuthenticationToken(
                "code", clientPrincipal, "http://localhost/cb", null);
        OAuth2TokenContext ctx = DefaultOAuth2TokenContext.builder().registeredClient(publicClient)
                .principal(new UsernamePasswordAuthenticationToken("account-1", null))
                .authorization(authorization(binding(Role.USER, NOW.plus(Duration.ofDays(30)))))
                .tokenType(OAuth2TokenType.REFRESH_TOKEN)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE).authorizationGrant(grant).build();

        assertThat(generator.generate(ctx)).isNull();
    }
}
