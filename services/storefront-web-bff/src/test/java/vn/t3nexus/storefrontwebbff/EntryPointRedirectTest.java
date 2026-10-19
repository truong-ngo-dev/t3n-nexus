package vn.t3nexus.storefrontwebbff;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import vn.t3nexus.storefrontwebbff.support.GatewayTestBase;

/**
 * KT-36 (feature 06, nhóm E, R30): điểm vào của BFF không phụ thuộc {@code contextPath}. {@code GET /bff/auth/login} chuyển hướng tới
 * {@code /oauth2/authorization/storefront-web} (đường dẫn của host {@code shop}, không tiền tố), có hay không có {@code X-Forwarded-Prefix}.
 * Cổng đã xóa tiêu đề này, nhưng BFF vẫn phải đúng khi nó lọt tới (gọi thẳng cổng 8090).
 * <p>
 * Từ nhóm B (bảng hành vi lỗi): {@code /api/**} chưa có phiên không còn chuyển hướng ở bất kỳ {@code Accept} nào, luôn 401 (người gọi là
 * fetch của SPA, 302 sang host khác thành lỗi CORS); các ca 401 đầy đủ ở {@link ErrorResponsesTest}.
 */
class EntryPointRedirectTest extends GatewayTestBase {

    private static final String LOGIN_PATH = "/oauth2/authorization/storefront-web";

    @Test
    void a_protected_route_without_a_session_is_401_even_for_html_and_with_a_forwarded_prefix() {
        client().get().uri("/api/customer/me").accept(MediaType.TEXT_HTML).exchange().expectStatus().isUnauthorized();
        client().get().uri("/api/customer/me").accept(MediaType.TEXT_HTML).header("X-Forwarded-Prefix", "/web").exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void the_login_endpoint_redirects_to_the_login_start_with_or_without_a_forwarded_prefix() {
        client().get().uri("/bff/auth/login").exchange()
                .expectStatus().is3xxRedirection()
                .expectHeader().valueEquals("Location", LOGIN_PATH);

        client().get().uri("/bff/auth/login").header("X-Forwarded-Prefix", "/web").exchange()
                .expectStatus().is3xxRedirection()
                .expectHeader().valueEquals("Location", LOGIN_PATH);
    }
}
