package vn.t3nexus.storefrontwebbff;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;
import vn.t3nexus.storefrontwebbff.support.GatewayTestBase;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * KT-08, 09, 11, 12, 34 (feature 06, nhóm B, ADR-0005, `tech.md` T4): CSRF ở BFF theo cách gửi hai lần (cookie {@code XSRF-TOKEN} đọc được bằng
 * JavaScript và tiêu đề {@code X-XSRF-TOKEN}). Yêu cầu ghi thiếu hoặc sai mã nhận 403 {@code CSRF_TOKEN_INVALID} (phiên không bị hủy);
 * {@code GET} không cần mã và phát cookie khi thiếu; mã xoay khi đăng nhập xong, xóa khi đăng xuất; {@code /bff/internal/**} không cần CSRF.
 * Mọi ca gửi {@code Accept: application/json} như giao diện thật.
 */
class CsrfTest extends GatewayTestBase {

    private static final String JSON = "application/json";

    private WebTestClient.ResponseSpec postOrder(String cookieHeader, String xsrfHeader) {
        var request = client().post().uri("/api/customer/orders").header("Accept", JSON).header("Content-Type", JSON);
        if (cookieHeader != null) request.header("Cookie", cookieHeader);
        if (xsrfHeader != null) request.header("X-XSRF-TOKEN", xsrfHeader);
        return request.bodyValue("{}").exchange();
    }

    private static List<String> setCookies(WebTestClient.ResponseSpec spec) {
        return spec.returnResult(Void.class).getResponseHeaders().get("Set-Cookie");
    }

    @Test
    void a_get_needs_no_token_and_the_first_one_issues_a_readable_session_scoped_cookie() {
        var result = client().get().uri("/bff/auth/session").header("Accept", JSON).exchange()
                .expectStatus().isUnauthorized().returnResult(Void.class);   // 401 của thăm dò, không phải 403: GET không cần mã

        String cookie = result.getResponseHeaders().get("Set-Cookie").stream().filter(c -> c.startsWith("XSRF-TOKEN=")).findFirst().orElseThrow();
        assertThat(cookie).doesNotContainIgnoringCase("HttpOnly").doesNotContainIgnoringCase("Max-Age")
                .doesNotContainIgnoringCase("Expires").contains("Path=/").containsIgnoringCase("SameSite=Lax");
    }

    @Test
    void the_session_cookie_stays_http_only_while_the_csrf_cookie_is_readable() {
        var login = login("user-1", UUID.randomUUID().toString(), "USER");

        var result = client().get().uri("/oauth2/authorization/storefront-web").exchange().returnResult(Void.class);
        List<String> cookies = result.getResponseHeaders().get("Set-Cookie");
        assertThat(cookies.stream().filter(c -> c.startsWith("STOREFRONT_SESSION=")).findFirst().orElseThrow()).containsIgnoringCase("HttpOnly");
        assertThat(cookies.stream().filter(c -> c.startsWith("XSRF-TOKEN=")).findFirst().orElseThrow()).doesNotContainIgnoringCase("HttpOnly");
        assertThat(login.xsrfCookie()).isNotBlank();
    }

    @Test
    void a_write_without_or_with_a_wrong_token_is_403_csrf_token_invalid_and_the_session_survives() {
        var login = login("user-1", UUID.randomUUID().toString(), "USER");
        CUSTOMER.respond("POST", "/api/orders", 201, JSON, "{}");

        postOrder(login.cookieHeader(), null).expectStatus().isForbidden()
                .expectBody().jsonPath("$.success").isEqualTo(false).jsonPath("$.code").isEqualTo("CSRF_TOKEN_INVALID");
        postOrder(login.cookieHeader(), "forged").expectStatus().isForbidden()
                .expectBody().jsonPath("$.code").isEqualTo("CSRF_TOKEN_INVALID");
        assertThat(CUSTOMER.requests()).as("không yêu cầu nào tới dịch vụ").isEmpty();

        client().get().uri("/bff/auth/session").header("Cookie", login.cookieHeader()).exchange().expectStatus().isOk();   // phiên còn
    }

    @Test
    void a_write_with_the_matching_token_is_forwarded() {
        var login = login("user-1", UUID.randomUUID().toString(), "USER");
        CUSTOMER.respond("POST", "/api/orders", 201, JSON, "{}");

        postOrder(login.cookieHeader(), login.xsrfCookie()).expectStatus().isCreated();

        assertThat(CUSTOMER.requests()).hasSize(1);
    }

    @Test
    void with_a_lost_cookie_a_light_get_issues_a_new_one_and_the_retry_succeeds_without_logging_in_again() {
        var login = login("user-1", UUID.randomUUID().toString(), "USER");
        CUSTOMER.respond("POST", "/api/orders", 201, JSON, "{}");
        String sessionOnly = "STOREFRONT_SESSION=" + login.sessionCookie();

        postOrder(sessionOnly, null).expectStatus().isForbidden();

        var probe = client().get().uri("/bff/auth/session").header("Cookie", sessionOnly).exchange().expectStatus().isOk();
        String fresh = cookieValue(setCookies(probe), "XSRF-TOKEN", null);
        assertThat(fresh).isNotBlank();

        postOrder(sessionOnly + "; XSRF-TOKEN=" + fresh, fresh).expectStatus().isCreated();
    }

    @Test
    void logout_needs_the_token_but_the_internal_back_channel_does_not() {
        String sid = UUID.randomUUID().toString();
        var login = login("user-1", sid, "USER");

        client().post().uri("/bff/auth/logout").header("Cookie", login.cookieHeader()).exchange().expectStatus().isForbidden();

        String serviceToken = IDP.jwt(Map.of("sub", "oauth2-service-internal", "scope", "storefront.internal"), Instant.now().plusSeconds(60));
        client().post().uri("/bff/internal/sessions/revoke").header("Authorization", "Bearer " + serviceToken)
                .header("Content-Type", JSON).bodyValue("{\"sid\":\"" + sid + "\"}").exchange().expectStatus().isOk();   // không cookie, không mã CSRF
    }

    @Test
    void the_token_is_rotated_by_a_successful_callback_and_cleared_by_logout() {
        var login = login("user-1", UUID.randomUUID().toString(), "USER");

        assertThat(login.xsrfAtStart()).as("cookie phát từ yêu cầu đầu tiên").isNotBlank();
        assertThat(login.xsrfCookie()).as("callback xoay mã").isNotBlank().isNotEqualTo(login.xsrfAtStart());

        var logout = client().post().uri("/bff/auth/logout").header("Cookie", login.cookieHeader()).header("X-XSRF-TOKEN", login.xsrfCookie())
                .exchange().expectStatus().isAccepted();
        String cleared = setCookies(logout).stream().filter(c -> c.startsWith("XSRF-TOKEN=")).findFirst().orElseThrow();
        assertThat(cleared).containsIgnoringCase("Max-Age=0");
    }

    @Test
    void a_lost_session_with_a_still_matching_cookie_gives_401_not_403() {
        String sid = UUID.randomUUID().toString();
        var login = login("user-1", sid, "USER");
        String serviceToken = IDP.jwt(Map.of("sub", "oauth2-service-internal", "scope", "storefront.internal"), Instant.now().plusSeconds(60));
        client().post().uri("/bff/internal/sessions/revoke").header("Authorization", "Bearer " + serviceToken)
                .header("Content-Type", JSON).bodyValue("{\"sid\":\"" + sid + "\"}").exchange().expectStatus().isOk();

        postOrder(login.cookieHeader(), login.xsrfCookie()).expectStatus().isUnauthorized();
    }
}
