package vn.t3nexus.storefrontwebbff;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import vn.t3nexus.storefrontwebbff.support.GatewayTestBase;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Chứng minh bộ khung test của BFF chạy đúng (feature 06, bước 4): ứng dụng thật, Redis thật, dịch vụ sau giả. Ba ca đặc tả hành vi
 * hiện có (thăm dò phiên, tuyến công khai viết lại đường dẫn, chuyển hướng hoặc 401 khi chưa đăng nhập) để có chỗ so khi các nhóm
 * E, B, C, D (feature 06) đổi chúng; test của từng nhóm viết ở các bước sau.
 */
class GatewayFrameworkTest extends GatewayTestBase {

    @Test
    void the_session_probe_answers_401_without_a_session() {
        client().get().uri("/bff/auth/session").exchange().expectStatus().isUnauthorized();
    }

    @Test
    void a_public_identity_route_reaches_the_identity_service_with_the_rewritten_path() {
        IDENTITY.respond("GET", "/api/users/verify", 200, "application/json", "{\"ok\":true}");

        client().get().uri("/api/identity/users/verify?token=abc").exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("{\"ok\":true}");

        assertThat(IDENTITY.last().path()).isEqualTo("/api/users/verify");
        assertThat(IDENTITY.last().query()).isEqualTo("token=abc");
        assertThat(CUSTOMER.requests()).as("không dịch vụ khác nhận yêu cầu").isEmpty();
    }

    @Test
    void a_protected_route_without_a_session_gives_401_for_json_and_for_html() {
        client().get().uri("/api/customer/me").accept(MediaType.APPLICATION_JSON).exchange()
                .expectStatus().isUnauthorized();

        client().get().uri("/api/customer/me").accept(MediaType.TEXT_HTML).exchange()
                .expectStatus().isUnauthorized();

        assertThat(CUSTOMER.requests()).as("yêu cầu chưa đăng nhập không tới dịch vụ sau").isEmpty();
    }
}
