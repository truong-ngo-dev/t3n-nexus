package vn.t3nexus.apigateway;

import org.junit.jupiter.api.Test;
import vn.t3nexus.apigateway.support.GatewayTestBase;
import vn.t3nexus.apigateway.support.RedisContainerHolder;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * KT-26 (feature 06, nhóm E, ADR-0006): định tuyến theo {@code Host}. Host {@code auth} tới Xác thực không tiền tố; host {@code shop},
 * {@code seller}, {@code ops} tới BFF với đường dẫn của BFF và tới máy chủ giao diện với đường dẫn còn lại; host ngoài danh sách
 * nhận 404 trước mọi bộ lọc nên không đếm gì và không tới dịch vụ nào.
 */
class HostRoutingTest extends GatewayTestBase {

    @Test
    void the_auth_host_goes_to_the_oauth2_service_without_any_prefix() {
        OAUTH2.respond("GET", "/login", 200, "text/plain", "trang-dang-nhap");

        get(AUTH_HOST, "/login").exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("trang-dang-nhap");

        assertThat(OAUTH2.last().path()).isEqualTo("/login");
        assertThat(BFF.requests()).isEmpty();
        assertThat(FRONTEND.requests()).isEmpty();
        assertThat(SELLER_BFF.requests()).isEmpty();
        assertThat(OPS_BFF.requests()).isEmpty();
        assertThat(SELLER_FRONTEND.requests()).isEmpty();
        assertThat(OPS_FRONTEND.requests()).isEmpty();
    }

    @Test
    void the_host_is_matched_ignoring_case_and_port() {
        OAUTH2.respond("GET", "/login", 200, "text/plain", "ok");

        get("AUTH.Localhost", "/login").exchange().expectStatus().isOk();
        get("auth.localhost:1234", "/login").exchange().expectStatus().isOk();

        assertThat(OAUTH2.requests()).hasSize(2);
    }

    @Test
    void the_shop_host_sends_bff_paths_to_the_bff() {
        for (String path : new String[]{"/bff/auth/session", "/api/identity/users/verify", "/oauth2/authorization/storefront-web",
                "/login/oauth2/code/storefront-web"}) {
            BFF.respond("GET", path, 200, "text/plain", "bff");

            get(SHOP_HOST, path).exchange().expectStatus().isOk().expectBody(String.class).isEqualTo("bff");

            assertThat(BFF.last().path()).as(path).isEqualTo(path);
        }
        assertThat(FRONTEND.requests()).as("đường dẫn BFF không tới máy chủ giao diện").isEmpty();
        assertThat(OAUTH2.requests()).isEmpty();
        assertThat(SELLER_BFF.requests()).isEmpty();
        assertThat(OPS_BFF.requests()).isEmpty();
    }

    @Test
    void the_shop_host_sends_every_other_path_to_the_frontend_server() {
        FRONTEND.respond("GET", "/", 200, "text/html", "<html>cua-hang</html>");
        FRONTEND.respond("GET", "/customer", 200, "text/html", "<html>khach</html>");

        get(SHOP_HOST, "/").exchange().expectStatus().isOk().expectBody(String.class).isEqualTo("<html>cua-hang</html>");
        get(SHOP_HOST, "/customer").exchange().expectStatus().isOk();
        // tiền tố cũ không còn là đường dẫn của BFF
        get(SHOP_HOST, "/web/api/identity/x").exchange().expectStatus().isNotFound();

        assertThat(BFF.requests()).as("không yêu cầu nào tới BFF").isEmpty();
        assertThat(FRONTEND.requests()).extracting(r -> r.path()).containsExactly("/", "/customer", "/web/api/identity/x");
    }

    @Test
    void the_seller_host_sends_bff_paths_to_the_seller_bff() {
        for (String path : new String[]{"/api/x", "/bff/auth/session", "/oauth2/authorization/seller-web"}) {
            SELLER_BFF.respond("GET", path, 200, "text/plain", "seller-bff");

            get(SELLER_HOST, path).exchange().expectStatus().isOk().expectBody(String.class).isEqualTo("seller-bff");

            assertThat(SELLER_BFF.last().path()).as(path).isEqualTo(path);
        }
        assertThat(SELLER_FRONTEND.requests()).as("đường dẫn BFF không tới máy chủ giao diện").isEmpty();
        assertThat(BFF.requests()).isEmpty();
        assertThat(OPS_BFF.requests()).isEmpty();
        assertThat(OAUTH2.requests()).isEmpty();
    }

    @Test
    void the_seller_host_sends_every_other_path_to_the_seller_frontend_server() {
        SELLER_FRONTEND.respond("GET", "/", 200, "text/html", "<html>nguoi-ban</html>");
        SELLER_FRONTEND.respond("GET", "/products", 200, "text/html", "<html>san-pham</html>");

        get(SELLER_HOST, "/").exchange().expectStatus().isOk().expectBody(String.class).isEqualTo("<html>nguoi-ban</html>");
        get(SELLER_HOST, "/products").exchange().expectStatus().isOk();

        assertThat(SELLER_BFF.requests()).as("không yêu cầu nào tới BFF").isEmpty();
        assertThat(SELLER_FRONTEND.requests()).extracting(r -> r.path()).containsExactly("/", "/products");
    }

    @Test
    void the_ops_host_sends_bff_paths_to_the_ops_bff() {
        for (String path : new String[]{"/api/x", "/bff/auth/session", "/oauth2/authorization/seller-web"}) {
            OPS_BFF.respond("GET", path, 200, "text/plain", "ops-bff");

            get(OPS_HOST, path).exchange().expectStatus().isOk().expectBody(String.class).isEqualTo("ops-bff");

            assertThat(OPS_BFF.last().path()).as(path).isEqualTo(path);
        }
        assertThat(OPS_FRONTEND.requests()).as("đường dẫn BFF không tới máy chủ giao diện").isEmpty();
        assertThat(BFF.requests()).isEmpty();
        assertThat(SELLER_BFF.requests()).isEmpty();
        assertThat(OAUTH2.requests()).isEmpty();
    }

    @Test
    void the_ops_host_sends_every_other_path_to_the_ops_frontend_server() {
        OPS_FRONTEND.respond("GET", "/", 200, "text/html", "<html>van-hanh</html>");
        OPS_FRONTEND.respond("GET", "/products", 200, "text/html", "<html>san-pham</html>");

        get(OPS_HOST, "/").exchange().expectStatus().isOk().expectBody(String.class).isEqualTo("<html>van-hanh</html>");
        get(OPS_HOST, "/products").exchange().expectStatus().isOk();

        assertThat(OPS_BFF.requests()).as("không yêu cầu nào tới BFF").isEmpty();
        assertThat(OPS_FRONTEND.requests()).extracting(r -> r.path()).containsExactly("/", "/products");
    }

    @Test
    void a_host_outside_the_list_gets_404_and_counts_nothing() {
        for (String host : new String[]{"evil.example", "localhost:8000", "127.0.0.1", "auth.localhost.evil.example", "unknown.localhost:8000"}) {
            get(host, "/login").exchange().expectStatus().isNotFound();
        }

        assertThat(OAUTH2.requests()).isEmpty();
        assertThat(BFF.requests()).isEmpty();
        assertThat(FRONTEND.requests()).isEmpty();
        assertThat(SELLER_BFF.requests()).isEmpty();
        assertThat(OPS_BFF.requests()).isEmpty();
        assertThat(SELLER_FRONTEND.requests()).isEmpty();
        assertThat(OPS_FRONTEND.requests()).isEmpty();
        assertThat(RedisContainerHolder.keys("ratelimit:*")).as("host lạ không làm tăng bộ đếm nào").isEmpty();
    }
}
