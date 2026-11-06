package vn.t3nexus.oauth2.infrastructure.security.oauth2.client;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.PropertySourcesPlaceholdersResolver;
import org.springframework.boot.env.PropertiesPropertySourceLoader;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.env.MockEnvironment;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Feature 10: cấu hình thật ({@code application.properties}) gắn đúng vào {@link BffClientsProperties}: ba BFF, mã client, địa chỉ nội bộ,
 * URI chuyển hướng suy ra từ địa chỉ giao diện của từng ứng dụng. Bắt lỗi gắn rỗng im lặng (seeder và bộ báo chạy trên bản đồ rỗng).
 */
class BffClientsPropertiesBindingTest {

    private BffClientsProperties bind(String... overrides) throws IOException {
        MockEnvironment env = new MockEnvironment();
        MutablePropertySources sources = env.getPropertySources();
        for (String o : overrides) {
            String[] kv = o.split("=", 2);
            env.setProperty(kv[0], kv[1]);
        }
        for (PropertySource<?> ps : new PropertiesPropertySourceLoader().load("application", new ClassPathResource("application.properties"))) {
            sources.addLast(ps);
        }
        Binder binder = new Binder(org.springframework.boot.context.properties.source.ConfigurationPropertySources.get(env),
                new PropertySourcesPlaceholdersResolver(env));
        return binder.bind("app.bffs", Bindable.of(BffClientsProperties.class)).get();
    }

    @Test
    void the_three_web_applications_are_bound_with_their_own_client_and_addresses() throws IOException {
        BffClientsProperties bffs = bind();

        assertThat(bffs.keySet()).containsExactly("storefront", "seller", "ops");
        assertThat(bffs.get("storefront").getClientId()).isEqualTo("storefront-web");
        assertThat(bffs.get("seller").getClientId()).isEqualTo("seller-web");
        assertThat(bffs.get("ops").getClientId()).isEqualTo("ops-web");
        assertThat(bffs.get("storefront").getBaseUrl()).isEqualTo("http://localhost:8090");
        assertThat(bffs.get("seller").getBaseUrl()).isEqualTo("http://localhost:8091");
        assertThat(bffs.get("ops").getBaseUrl()).isEqualTo("http://localhost:8092");
        assertThat(bffs.get("seller").getRedirectUris()).containsExactly("http://seller.localhost:8000/login/oauth2/code/seller-web");
        assertThat(bffs.get("seller").getPostLogoutRedirectUris()).containsExactly("http://seller.localhost:8000/");
        assertThat(bffs.get("ops").getRedirectUris()).containsExactly("http://ops.localhost:8000/login/oauth2/code/ops-web");
        assertThat(bffs.get("storefront").getRedirectUris()).containsExactly("http://shop.localhost:8000/login/oauth2/code/storefront-web");
        assertThat(bffs.values()).allSatisfy(b -> assertThat(b.isRequireProofKey()).as("PKCE bắt buộc theo mặc định").isTrue());
    }

    @Test
    void the_client_is_found_by_its_public_id_and_nothing_else() throws IOException {
        BffClientsProperties bffs = bind();

        assertThat(bffs.byClientId("seller-web")).get().extracting(java.util.Map.Entry::getKey).isEqualTo("seller");
        assertThat(bffs.byClientId("postman-dev")).isEmpty();
        assertThat(bffs.byClientId(null)).isEmpty();
        assertThat(BffClientsProperties.internalScope("ops")).isEqualTo("ops.internal");
    }

    @Test
    void environment_overrides_reach_the_bound_values() throws IOException {
        BffClientsProperties bffs = bind("SELLER_BFF_URL=http://seller-bff:9000", "OPS_WEB_REQUIRE_PKCE=false");

        assertThat(bffs.get("seller").getBaseUrl()).isEqualTo("http://seller-bff:9000");
        assertThat(bffs.get("ops").isRequireProofKey()).isFalse();
        assertThat(bffs.get("seller").isRequireProofKey()).isTrue();
    }
}
