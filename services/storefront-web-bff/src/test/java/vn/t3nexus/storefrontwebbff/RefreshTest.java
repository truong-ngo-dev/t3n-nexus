package vn.t3nexus.storefrontwebbff;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.session.ReactiveSessionRepository;
import org.springframework.session.Session;
import vn.t3nexus.storefrontwebbff.support.GatewayTestBase;
import vn.t3nexus.storefrontwebbff.support.RedisContainerHolder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * KT-13, KT-14, KT-40 (feature 06, nhóm C, ADR-0003 mục 8, R12, `tech.md` T5). Token truy cập đăng nhập với {@code expires_in=1} nên coi như
 * hết hạn ngay (độ lệch đồng hồ 5 giây), mọi lời gọi {@code /api/**} đều phải làm mới trước.
 * <ul>
 *   <li>KT-13: làm mới kèm {@code sid} lấy từ token hiện có, lưu token mới, yêu cầu đi tiếp với token mới.</li>
 *   <li>KT-14: {@code invalid_grant} thì xóa phiên và ánh xạ, trả 401, không làm mới lần nữa.</li>
 *   <li>KT-40: lỗi tạm thời (5xx) thì 503 và giữ phiên, ánh xạ; Xác thực hồi phục thì yêu cầu kế tiếp làm mới được mà không đăng nhập lại.</li>
 * </ul>
 */
class RefreshTest extends GatewayTestBase {

    @Autowired
    private ReactiveSessionRepository<? extends Session> sessions;

    private LoginResult expiredLogin(String sid) {
        return login("user-1", sid, "USER", 1);
    }

    private org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec callApi(String cookie) {
        return client().get().uri("/api/customer/me").accept(org.springframework.http.MediaType.APPLICATION_JSON)
                .header("Cookie", "STOREFRONT_SESSION=" + cookie).exchange();
    }

    @Test
    void expired_access_token_is_refreshed_with_sid_and_the_new_token_reaches_the_service() {
        String sid = UUID.randomUUID().toString();
        var login = expiredLogin(sid);
        respondToRefreshWithNewTokens("user-1", sid, "USER", "second");
        CUSTOMER.respond("GET", "/api/me", 200, "application/json", "{}");

        callApi(login.sessionCookie()).expectStatus().isOk();

        assertThat(refreshRequests()).hasSize(1);
        String body = refreshRequests().get(0).body();
        assertThat(body).contains("refresh_token=refresh-user-1").contains("sid=" + sid);
        String bearer = CUSTOMER.last().header("Authorization");
        assertThat(bearer).startsWith("Bearer ");
        assertThat(new String(java.util.Base64.getUrlDecoder().decode(bearer.split("\\.")[1]))).contains("\"marker\":\"second\"");
    }

    @Test
    void the_refreshed_token_is_stored_so_the_next_request_does_not_refresh_again() {
        String sid = UUID.randomUUID().toString();
        var login = expiredLogin(sid);
        respondToRefreshWithNewTokens("user-1", sid, "USER", "second");
        CUSTOMER.respond("GET", "/api/me", 200, "application/json", "{}");

        callApi(login.sessionCookie()).expectStatus().isOk();
        callApi(login.sessionCookie()).expectStatus().isOk();

        assertThat(refreshRequests()).as("lần hai dùng token đã lưu").hasSize(1);
    }

    @Test
    void invalid_grant_deletes_the_session_and_mapping_answers_401_and_is_not_retried() {
        String sid = UUID.randomUUID().toString();
        var login = expiredLogin(sid);
        OAUTH2.respond("POST", "/oauth2/token", 400, "application/json", "{\"error\":\"invalid_grant\"}");
        CUSTOMER.respond("GET", "/api/me", 200, "application/json", "{}");

        callApi(login.sessionCookie()).expectStatus().isUnauthorized().expectBody().jsonPath("$.code").isEqualTo("UNAUTHENTICATED");

        assertThat(RedisContainerHolder.exists("storefront:sid:" + sid)).isFalse();
        assertThat(RedisContainerHolder.exists("storefront:session:" + login.sessionCookie())).isFalse();
        assertThat(sessions.findById(login.sessionCookie()).block()).isNull();
        assertThat(CUSTOMER.requests()).as("yêu cầu không tới dịch vụ").isEmpty();

        callApi(login.sessionCookie()).expectStatus().isUnauthorized();
        assertThat(refreshRequests()).as("phiên đã mất nên không làm mới lần nữa").hasSize(1);
    }

    @Test
    void temporary_failure_gives_503_keeps_session_and_mapping_and_recovers_without_login() {
        String sid = UUID.randomUUID().toString();
        var login = expiredLogin(sid);
        OAUTH2.respond("POST", "/oauth2/token", 503, "text/plain", "down");
        CUSTOMER.respond("GET", "/api/me", 200, "application/json", "{}");

        callApi(login.sessionCookie()).expectStatus().isEqualTo(503).expectBody().jsonPath("$.code").isEqualTo("SERVICE_UNAVAILABLE");

        assertThat(RedisContainerHolder.exists("storefront:sid:" + sid)).isTrue();
        assertThat(sessions.findById(login.sessionCookie()).block()).isNotNull();
        assertThat(CUSTOMER.requests()).isEmpty();

        respondToRefreshWithNewTokens("user-1", sid, "USER", "recovered");
        callApi(login.sessionCookie()).expectStatus().isOk();
        assertThat(CUSTOMER.requests()).hasSize(1);
    }

    @Test
    void server_error_500_is_also_temporary() {
        String sid = UUID.randomUUID().toString();
        var login = expiredLogin(sid);
        OAUTH2.respond("POST", "/oauth2/token", 500, "application/json", "{\"error\":\"server_error\"}");

        callApi(login.sessionCookie()).expectStatus().isEqualTo(503);
        assertThat(sessions.findById(login.sessionCookie()).block()).isNotNull();
    }

    @Test
    void a_token_with_30_seconds_left_is_not_refreshed_because_the_skew_is_5_seconds() {
        String sid = UUID.randomUUID().toString();
        var login = login("user-1", sid, "USER", 30);
        CUSTOMER.respond("GET", "/api/me", 200, "application/json", "{}");

        callApi(login.sessionCookie()).expectStatus().isOk();

        assertThat(refreshRequests()).isEmpty();
    }
}
