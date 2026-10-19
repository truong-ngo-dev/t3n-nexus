package vn.t3nexus.storefrontwebbff;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.session.ReactiveSessionRepository;
import org.springframework.session.Session;
import org.springframework.test.web.reactive.server.WebTestClient;
import vn.t3nexus.storefrontwebbff.support.GatewayTestBase;
import vn.t3nexus.storefrontwebbff.support.RedisContainerHolder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Hành vi lỗi của BFF theo hai loại người gọi (feature 06, bảng "hành vi lỗi", chốt với người dùng):
 * <ul>
 *   <li>Loại 1, SPA gọi bằng fetch: không bao giờ 302. {@code /api/**} chưa có phiên luôn 401 khung lỗi chung {@code UNAUTHENTICATED},
 *       bất kể {@code Accept}; đường dẫn lạ 404 {@code NOT_FOUND}; dịch vụ sau trả 401 thì chuyển nguyên và giữ phiên; phiên còn nhưng mất
 *       client ủy quyền thì xóa phiên và 401 (không chuyển yêu cầu đi không có token); đăng xuất khi hết phiên vẫn 202 + {@code Location}
 *       về trang đích.</li>
 * </ul>
 */
class ErrorResponsesTest extends GatewayTestBase {

    private static final String JSON = "application/json";
    private static final String AUTHORIZED_CLIENTS_FIELD =
            "sessionAttr:org.springframework.security.oauth2.client.web.server.WebSessionServerOAuth2AuthorizedClientRepository.AUTHORIZED_CLIENTS";

    @Autowired
    private ReactiveSessionRepository<? extends Session> sessions;

    @Test
    void api_without_a_session_is_401_in_the_common_frame_whatever_the_accept_header() {
        for (String accept : new String[] {"text/html", "application/json", "*/*"}) {
            client().get().uri("/api/customer/me").header("Accept", accept).exchange()
                    .expectStatus().isUnauthorized()
                    .expectHeader().contentTypeCompatibleWith("application/json")
                    .expectBody().jsonPath("$.success").isEqualTo(false).jsonPath("$.code").isEqualTo("UNAUTHENTICATED");
        }
        client().get().uri("/api/customer/me").exchange().expectStatus().isUnauthorized();   // thiếu Accept
        assertThat(CUSTOMER.requests()).isEmpty();
    }

    @Test
    void an_unknown_path_is_404_in_the_common_frame() {
        client().get().uri("/nothing-here").header("Accept", JSON).exchange()
                .expectStatus().isNotFound()
                .expectBody().jsonPath("$.success").isEqualTo(false).jsonPath("$.code").isEqualTo("NOT_FOUND");
    }

    @Test
    void a_downstream_401_is_forwarded_as_it_is_and_the_session_is_kept() {
        var login = login("user-1", UUID.randomUUID().toString(), "USER");
        CUSTOMER.respond("GET", "/api/me", 401, JSON, "{\"from\":\"service\"}");

        client().get().uri("/api/customer/me").header("Accept", JSON).header("Cookie", login.cookieHeader()).exchange()
                .expectStatus().isUnauthorized().expectBody().jsonPath("$.from").isEqualTo("service");

        assertThat(sessions.findById(login.sessionCookie()).block()).isNotNull();
        client().get().uri("/bff/auth/session").header("Cookie", login.cookieHeader()).exchange().expectStatus().isOk();
    }

    @Test
    void a_session_that_lost_its_authorized_client_is_dropped_and_answers_401_instead_of_relaying_without_a_token() {
        String sid = UUID.randomUUID().toString();
        var login = login("user-1", sid, "USER");
        CUSTOMER.respond("GET", "/api/me", 200, JSON, "{}");
        String sessionKey = "spring:session:sessions:" + login.sessionCookie();
        assertThat(RedisContainerHolder.hkeys(sessionKey)).as("trường chứa client ủy quyền").contains(AUTHORIZED_CLIENTS_FIELD);
        RedisContainerHolder.hdel(sessionKey, AUTHORIZED_CLIENTS_FIELD);

        client().get().uri("/api/customer/me").header("Accept", JSON).header("Cookie", login.cookieHeader()).exchange()
                .expectStatus().isUnauthorized()
                .expectBody().jsonPath("$.code").isEqualTo("UNAUTHENTICATED");

        assertThat(CUSTOMER.requests()).as("không chuyển đi không có token").isEmpty();
        assertThat(sessions.findById(login.sessionCookie()).block()).isNull();
        assertThat(RedisContainerHolder.sessionsHoldingTokens()).as("không còn phiên giữ token dưới mã nào").isZero();
        assertThat(RedisContainerHolder.exists("storefront:sid:" + sid)).isFalse();
    }

    @Test
    void logout_with_no_session_still_answers_202_with_the_landing_page_location() {
        var probe = client().get().uri("/bff/auth/session").exchange().returnResult(Void.class);
        String xsrf = cookieValue(probe.getResponseHeaders().get("Set-Cookie"), "XSRF-TOKEN", null);
        assertThat(xsrf).isNotBlank();

        WebTestClient.ResponseSpec response = client().post().uri("/bff/auth/logout")
                .header("Cookie", "XSRF-TOKEN=" + xsrf).header("X-XSRF-TOKEN", xsrf).header("Accept", JSON).exchange();

        response.expectStatus().isAccepted().expectHeader().valueEquals("Location", "http://shop.localhost:8000/");
    }
}
