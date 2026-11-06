package vn.t3nexus.oauth2.infrastructure.security.oauth2.client;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RegisteredClientSeederTest {

    private final PasswordEncoder passwordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();

    private RegisteredClientRepository createRepository() {
        RegisteredClient storefront = RegisteredClient.withId("storefront-web-client-id")
                .clientId("storefront-web")
                .clientSecret(passwordEncoder.encode("secret"))
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .scope(OidcScopes.OPENID)
                .scope(OidcScopes.PROFILE)
                .scope(OidcScopes.EMAIL)
                .redirectUri("http://localhost/cb")
                .build();

        RegisteredClient internal = RegisteredClient.withId("oauth2-service-internal-client-id")
                .clientId("oauth2-service-internal")
                .clientSecret(passwordEncoder.encode("internal-secret"))
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .scope("storefront.internal")
                .build();

        return new InMemoryRegisteredClientRepository(storefront, internal);
    }

    private BffClientsProperties sampleBffs() {
        BffClientsProperties props = new BffClientsProperties();

        BffClientsProperties.Bff storefront = new BffClientsProperties.Bff();
        storefront.setClientId("storefront-web");
        storefront.setSecret("secret");
        storefront.setBaseUrl("http://localhost:8090");
        storefront.setRedirectUris(List.of("http://shop.localhost:8000/login/oauth2/code/storefront-web"));
        storefront.setPostLogoutRedirectUris(List.of("http://shop.localhost:8000/"));
        storefront.setRequireProofKey(true);
        props.put("storefront", storefront);

        BffClientsProperties.Bff seller = new BffClientsProperties.Bff();
        seller.setClientId("seller-web");
        seller.setSecret("secret");
        seller.setBaseUrl("http://localhost:8091");
        seller.setRedirectUris(List.of("http://seller.localhost:8000/login/oauth2/code/seller-web"));
        seller.setPostLogoutRedirectUris(List.of("http://seller.localhost:8000/"));
        seller.setRequireProofKey(true);
        props.put("seller", seller);

        BffClientsProperties.Bff ops = new BffClientsProperties.Bff();
        ops.setClientId("ops-web");
        ops.setSecret("secret");
        ops.setBaseUrl("http://localhost:8092");
        ops.setRedirectUris(List.of("http://ops.localhost:8000/login/oauth2/code/ops-web"));
        ops.setPostLogoutRedirectUris(List.of("http://ops.localhost:8000/"));
        ops.setRequireProofKey(true);
        props.put("ops", ops);

        return props;
    }

    @Test
    void seeds_seller_and_ops_with_matching_grant_types_and_properties() {
        RegisteredClientRepository repository = createRepository();
        MockEnvironment env = new MockEnvironment();
        RegisteredClientSeeder seeder = new RegisteredClientSeeder(
                repository, passwordEncoder, env, sampleBffs(), "oauth2-service-internal", "internal-secret"
        );

        seeder.run(null);

        RegisteredClient seller = repository.findByClientId("seller-web");
        assertThat(seller).isNotNull();
        assertThat(seller.getClientId()).isEqualTo("seller-web");
        assertThat(seller.getRedirectUris()).containsExactly("http://seller.localhost:8000/login/oauth2/code/seller-web");
        assertThat(seller.getPostLogoutRedirectUris()).containsExactly("http://seller.localhost:8000/");
        assertThat(seller.getClientSettings().isRequireProofKey()).isTrue();
        assertThat(seller.getAuthorizationGrantTypes()).containsExactlyInAnyOrder(
                AuthorizationGrantType.AUTHORIZATION_CODE, AuthorizationGrantType.REFRESH_TOKEN
        );

        RegisteredClient ops = repository.findByClientId("ops-web");
        assertThat(ops).isNotNull();
        assertThat(ops.getClientId()).isEqualTo("ops-web");
        assertThat(ops.getRedirectUris()).containsExactly("http://ops.localhost:8000/login/oauth2/code/ops-web");
        assertThat(ops.getPostLogoutRedirectUris()).containsExactly("http://ops.localhost:8000/");
        assertThat(ops.getClientSettings().isRequireProofKey()).isTrue();
        assertThat(ops.getAuthorizationGrantTypes()).containsExactlyInAnyOrder(
                AuthorizationGrantType.AUTHORIZATION_CODE, AuthorizationGrantType.REFRESH_TOKEN
        );
    }

    @Test
    void internal_client_scopes_contain_scopes_for_all_bffs() {
        RegisteredClientRepository repository = createRepository();
        MockEnvironment env = new MockEnvironment();
        RegisteredClientSeeder seeder = new RegisteredClientSeeder(
                repository, passwordEncoder, env, sampleBffs(), "oauth2-service-internal", "internal-secret"
        );

        seeder.run(null);

        RegisteredClient internal = repository.findByClientId("oauth2-service-internal");
        assertThat(internal).isNotNull();
        assertThat(internal.getScopes()).contains("storefront.internal", "seller.internal", "ops.internal");
    }

    @Test
    void throws_illegal_state_exception_outside_dev_when_secret_is_missing() {
        RegisteredClientRepository repository = createRepository();
        MockEnvironment env = new MockEnvironment();
        BffClientsProperties props = sampleBffs();
        props.get("ops").setSecret("");

        RegisteredClientSeeder seeder = new RegisteredClientSeeder(
                repository, passwordEncoder, env, props, "oauth2-service-internal", "internal-secret"
        );

        assertThatThrownBy(() -> seeder.run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.bffs.ops.secret");
    }

    @Test
    void does_not_throw_in_dev_profile_when_secret_is_missing() {
        RegisteredClientRepository repository = createRepository();
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("dev");
        BffClientsProperties props = sampleBffs();
        props.get("ops").setSecret("");

        RegisteredClientSeeder seeder = new RegisteredClientSeeder(
                repository, passwordEncoder, env, props, "oauth2-service-internal", "internal-secret"
        );

        seeder.run(null);

        RegisteredClient ops = repository.findByClientId("ops-web");
        assertThat(ops).isNotNull();
    }

    @Test
    void running_twice_keeps_single_client_and_does_not_change_id() {
        RegisteredClientRepository repository = createRepository();
        MockEnvironment env = new MockEnvironment();
        RegisteredClientSeeder seeder = new RegisteredClientSeeder(
                repository, passwordEncoder, env, sampleBffs(), "oauth2-service-internal", "internal-secret"
        );

        seeder.run(null);
        RegisteredClient firstRun = repository.findByClientId("seller-web");
        String firstId = firstRun.getId();

        seeder.run(null);
        RegisteredClient secondRun = repository.findByClientId("seller-web");
        assertThat(secondRun.getId()).isEqualTo(firstId);
    }

    @Test
    void require_proof_key_false_is_applied() {
        RegisteredClientRepository repository = createRepository();
        MockEnvironment env = new MockEnvironment();
        BffClientsProperties props = sampleBffs();
        props.get("ops").setRequireProofKey(false);

        RegisteredClientSeeder seeder = new RegisteredClientSeeder(
                repository, passwordEncoder, env, props, "oauth2-service-internal", "internal-secret"
        );

        seeder.run(null);

        RegisteredClient ops = repository.findByClientId("ops-web");
        assertThat(ops).isNotNull();
        assertThat(ops.getClientSettings().isRequireProofKey()).isFalse();
    }
}
