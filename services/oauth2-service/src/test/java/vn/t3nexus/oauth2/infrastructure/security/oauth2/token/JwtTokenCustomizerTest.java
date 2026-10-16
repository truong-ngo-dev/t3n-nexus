package vn.t3nexus.oauth2.infrastructure.security.oauth2.token;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.oidc.endpoint.OidcParameterNames;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import vn.t3nexus.oauth2.domain.login_session.SessionLifetimePolicy;
import vn.t3nexus.oauth2.domain.login_session.SessionLifetimePolicy.Lifetimes;
import vn.t3nexus.oauth2.domain.user_account.Role;
import vn.t3nexus.oauth2.infrastructure.security.oauth2.key.RsaKeyPairRepository;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * KT-18 (phần bộ tùy biến JWT) và KT-02, 25: `exp` cắt theo hạn phiên, claim `sid` và `role`, không có claim cũ
 * (`oss_id`, `roles`, `email`) và không có mã nội bộ.
 */
class JwtTokenCustomizerTest {

    private static final Instant NOW = Instant.parse("2026-10-14T03:00:00Z");

    private static final SessionLifetimePolicy POLICY = new SessionLifetimePolicy(
            new Lifetimes(Duration.ofMinutes(15), Duration.ofDays(7), Duration.ofDays(30)),
            new Lifetimes(Duration.ofMinutes(15), Duration.ofMinutes(30), Duration.ofHours(10)));

    private static final RegisteredClient CLIENT = RegisteredClient.withId("rc-1").clientId("web-gateway")
            .clientSecret("{noop}x").authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("http://localhost/cb").build();

    private final RsaKeyPairRepository keys = new RsaKeyPairRepository() {
        public List<RsaKeyPair> findKeyPairs() { return List.of(new RsaKeyPair("kid-newest", NOW, null, null)); }
        public void save(RsaKeyPair keyPair) { throw new UnsupportedOperationException(); }
    };

    private final JwtTokenCustomizer customizer =
            new JwtTokenCustomizer(keys, POLICY, Clock.fixed(NOW, ZoneOffset.UTC));

    private static SessionBinding binding(Role role, Instant sessionExpiresAt) {
        return new SessionBinding("login-session-1", "sid-1", sessionExpiresAt, role);
    }

    private static OAuth2Authorization authorization(SessionBinding binding) {
        OAuth2Authorization.Builder builder = OAuth2Authorization.withRegisteredClient(CLIENT)
                .principalName("account-1").authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE);
        if (binding != null) builder.attributes(attrs -> attrs.putAll(binding.toAttributes()));
        return builder.build();
    }

    private JwtEncodingContext context(OAuth2TokenType type, OAuth2Authorization authorization) {
        JwtEncodingContext.Builder builder = JwtEncodingContext.with(JwsHeader.with(SignatureAlgorithm.RS256),
                        JwtClaimsSet.builder().subject("account-1").issuedAt(NOW).expiresAt(NOW.plus(Duration.ofMinutes(30))))
                .registeredClient(CLIENT)
                .principal(new UsernamePasswordAuthenticationToken("account-1", null))
                .tokenType(type)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE);
        if (authorization != null) builder.authorization(authorization);   // client credentials không có bản ghi ủy quyền
        return builder.build();
    }

    private static OAuth2TokenType idToken() {
        return new OAuth2TokenType(OidcParameterNames.ID_TOKEN);
    }

    // ───────────── access token ─────────────

    @Test
    void access_token_carries_sid_and_role_and_the_signing_key_id() {
        JwtEncodingContext ctx = context(OAuth2TokenType.ACCESS_TOKEN, authorization(binding(Role.USER, NOW.plus(Duration.ofDays(30)))));

        customizer.customize(ctx);

        JwtClaimsSet claims = ctx.getClaims().build();
        assertThat(claims.getClaimAsString("sid")).isEqualTo("sid-1");
        assertThat(claims.getClaimAsString("role")).isEqualTo("USER");
        assertThat(claims.getSubject()).isEqualTo("account-1");
        assertThat(ctx.getJwsHeader().build().getKeyId()).isEqualTo("kid-newest");
    }

    @Test
    void access_token_expires_after_its_ttl_while_the_session_is_far_from_expiry() {
        JwtEncodingContext ctx = context(OAuth2TokenType.ACCESS_TOKEN, authorization(binding(Role.USER, NOW.plus(Duration.ofDays(30)))));

        customizer.customize(ctx);

        assertThat(ctx.getClaims().build().getExpiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(15)));
    }

    @Test
    void access_token_is_cut_to_the_session_expiry_when_the_session_ends_sooner() {
        Instant sessionExpiry = NOW.plus(Duration.ofMinutes(5));
        JwtEncodingContext ctx = context(OAuth2TokenType.ACCESS_TOKEN, authorization(binding(Role.USER, sessionExpiry)));

        customizer.customize(ctx);

        assertThat(ctx.getClaims().build().getExpiresAt()).isEqualTo(sessionExpiry);
    }

    @Test
    void internal_role_is_carried_as_the_role_claim() {
        JwtEncodingContext ctx = context(OAuth2TokenType.ACCESS_TOKEN, authorization(binding(Role.ADMIN, NOW.plus(Duration.ofHours(10)))));

        customizer.customize(ctx);

        assertThat(ctx.getClaims().build().getClaimAsString("role")).isEqualTo("ADMIN");
    }

    @Test
    void access_token_has_none_of_the_old_or_internal_claims() {
        JwtEncodingContext ctx = context(OAuth2TokenType.ACCESS_TOKEN, authorization(binding(Role.USER, NOW.plus(Duration.ofDays(30)))));

        customizer.customize(ctx);

        assertThat(ctx.getClaims().build().getClaims().keySet())
                .doesNotContain("oss_id", "roles", "email", "authorization_id", "login_session_id", "idp_session_id");
        assertThat(ctx.getClaims().build().getClaims().toString())
                .doesNotContain("login-session-1");        // mã phiên người dùng không vào token (ADR-0003 mục 4)
    }

    @Test
    void an_expired_session_gets_no_access_token() {
        JwtEncodingContext ctx = context(OAuth2TokenType.ACCESS_TOKEN, authorization(binding(Role.USER, NOW)));

        Throwable thrown = catchThrowable(() -> customizer.customize(ctx));

        assertThat(thrown).isInstanceOfSatisfying(OAuth2AuthenticationException.class,
                e -> assertThat(e.getError().getErrorCode()).isEqualTo(OAuth2ErrorCodes.INVALID_GRANT));
    }

    // ───────────── id token ─────────────

    @Test
    void id_token_carries_sid_and_the_same_expiry_as_the_access_token_but_no_role() {
        JwtEncodingContext ctx = context(idToken(), authorization(binding(Role.USER, NOW.plus(Duration.ofDays(30)))));

        customizer.customize(ctx);

        JwtClaimsSet claims = ctx.getClaims().build();
        assertThat(claims.getClaimAsString("sid")).isEqualTo("sid-1");
        assertThat(claims.getClaims()).doesNotContainKey("role").doesNotContainKey("oss_id");
        assertThat(claims.getExpiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(15)));
    }

    @Test
    void id_token_is_cut_to_the_session_expiry_too() {
        Instant sessionExpiry = NOW.plus(Duration.ofMinutes(2));
        JwtEncodingContext ctx = context(idToken(), authorization(binding(Role.USER, sessionExpiry)));

        customizer.customize(ctx);

        assertThat(ctx.getClaims().build().getExpiresAt()).isEqualTo(sessionExpiry);
    }

    // ───────────── token không thuộc phiên người dùng ─────────────

    @Test
    void a_token_that_is_not_part_of_a_user_session_only_gets_the_key_id() {
        JwtEncodingContext noBinding = context(OAuth2TokenType.ACCESS_TOKEN, authorization(null));
        JwtEncodingContext noAuthorization = context(OAuth2TokenType.ACCESS_TOKEN, null);

        customizer.customize(noBinding);
        customizer.customize(noAuthorization);

        for (JwtEncodingContext ctx : List.of(noBinding, noAuthorization)) {
            JwtClaimsSet claims = ctx.getClaims().build();
            assertThat(claims.getClaims()).doesNotContainKey("sid").doesNotContainKey("role");
            assertThat(claims.getExpiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(30)));   // giữ nguyên hạn mặc định
            assertThat(ctx.getJwsHeader().build().getKeyId()).isEqualTo("kid-newest");
        }
    }
}
