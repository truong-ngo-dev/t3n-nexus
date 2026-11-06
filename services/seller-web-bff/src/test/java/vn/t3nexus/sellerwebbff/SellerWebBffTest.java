package vn.t3nexus.sellerwebbff;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;
import vn.t3nexus.sellerwebbff.support.GatewayTestBase;
import vn.t3nexus.sellerwebbff.support.RedisContainerHolder;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Feature 10: BFF của ứng dụng Người bán dựng từ thư viện dùng chung. Chứng minh mọi thứ phụ thuộc tên ứng dụng
 * đã đổi theo: mã đăng ký OAuth2, cookie phiên, tiền tố khóa Redis, phạm vi token dịch vụ, trang đích sau đăng nhập và bảng tuyến.
 */
class SellerWebBffTest extends GatewayTestBase {

    private WebTestClient.ResponseSpec revoke(String scope, String sid) {
        String token = IDP.jwt(Map.of("sub", "oauth2-service-internal", "scope", scope), Instant.now().plusSeconds(60));
        return client().post().uri("/bff/internal/sessions/revoke").header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + token).bodyValue("{\"sid\":\"" + sid + "\"}").exchange();
    }

    @Test
    void the_login_entry_point_uses_this_applications_client() {
        client().get().uri("/bff/auth/login").exchange().expectStatus().isFound()
                .expectHeader().valueEquals("Location", "/oauth2/authorization/seller-web");

        var start = client().get().uri("/oauth2/authorization/seller-web")
                .header("X-Forwarded-Host", "seller.localhost:8000").header("X-Forwarded-Proto", "http").header("X-Forwarded-Port", "8000")
                .exchange().returnResult(Void.class);
        String location = start.getResponseHeaders().getFirst("Location");
        assertThat(location).contains("client_id=seller-web")
                .contains("redirect_uri=http://seller.localhost:8000/login/oauth2/code/seller-web");
        assertThat(String.join(";", start.getResponseHeaders().get("Set-Cookie"))).contains("SELLER_SESSION=").doesNotContain("STOREFRONT_SESSION");
    }

    @Test
    void a_successful_login_lands_on_this_applications_host_and_keys_are_prefixed_by_application() {
        String sid = UUID.randomUUID().toString();

        var login = login("user-1", sid, "USER");

        assertThat(login.status()).isEqualTo(302);
        assertThat(login.location()).isEqualTo("http://seller.localhost:8000/");
        assertThat(RedisContainerHolder.exists("seller:sid:" + sid)).isTrue();
        assertThat(RedisContainerHolder.exists("seller:session:" + login.sessionCookie())).isTrue();
        assertThat(RedisContainerHolder.keys("storefront:*")).as("không đụng khóa của Cửa hàng").isEmpty();
    }

    @Test
    void only_the_token_scope_of_this_application_may_revoke_sessions() {
        String sid = UUID.randomUUID().toString();
        var login = login("user-1", sid, "USER");

        revoke("storefront.internal", sid).expectStatus().isForbidden();
        revoke("ops.internal", sid).expectStatus().isForbidden();
        assertThat(RedisContainerHolder.exists("seller:sid:" + sid)).as("phạm vi sai không xóa gì").isTrue();

        revoke("seller.internal", sid).expectStatus().isOk();
        assertThat(RedisContainerHolder.exists("seller:sid:" + sid)).isFalse();
        assertThat(RedisContainerHolder.exists("seller:session:" + login.sessionCookie())).isFalse();
    }

    @Test
    void routes_reach_the_right_services_with_the_rewritten_path() {
        var login = login("user-1", UUID.randomUUID().toString(), "USER");

        CATALOG.respond("GET", "/api/seller/products", 200, "application/json", "{}");
        client().get().uri("/api/catalog/seller/products").header("Cookie", login.cookieHeader()).exchange().expectStatus().isOk();
        assertThat(CATALOG.last().path()).isEqualTo("/api/seller/products");

        SELLER_SERVICE.respond("GET", "/api/seller/profile", 200, "application/json", "{}");
        client().get().uri("/api/seller/seller/profile").header("Cookie", login.cookieHeader()).exchange().expectStatus().isOk();
        assertThat(SELLER_SERVICE.last().path()).isEqualTo("/api/seller/profile");
    }

    @Test
    void a_protected_route_without_a_session_is_401_and_reaches_no_service() {
        client().get().uri("/api/catalog/seller/products").exchange().expectStatus().isUnauthorized();

        assertThat(SELLER_SERVICE.requests()).isEmpty();
        assertThat(CATALOG.requests()).isEmpty();
    }

    @Test
    void the_other_applications_routes_do_not_exist_here() {
        client().get().uri("/api/customer/me").exchange().expectStatus().isUnauthorized();
        client().get().uri("/api/order/x").exchange().expectStatus().isUnauthorized();
    }
}
