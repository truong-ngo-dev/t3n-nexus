package vn.t3nexus.storefrontwebbff;

import org.junit.jupiter.api.Test;
import vn.t3nexus.storefrontwebbff.support.GatewayTestBase;

import java.util.UUID;

/**
 * KT-17 (feature 06, nhóm D, R16): thăm dò phiên trả {@code userId} = {@code sub} và {@code role} đọc từ claim {@code role} (chuỗi) của
 * token truy cập; chưa đăng nhập thì 401.
 */
class SessionProbeTest extends GatewayTestBase {

    @Test
    void probe_returns_sub_and_the_role_claim() {
        var login = login("user-42", UUID.randomUUID().toString(), "ADMIN");

        client().get().uri("/bff/auth/session").header("Cookie", "STOREFRONT_SESSION=" + login.sessionCookie())
                .exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.userId").isEqualTo("user-42").jsonPath("$.role").isEqualTo("ADMIN");
    }

    @Test
    void probe_without_a_session_is_401() {
        client().get().uri("/bff/auth/session").exchange().expectStatus().isUnauthorized();
    }
}
