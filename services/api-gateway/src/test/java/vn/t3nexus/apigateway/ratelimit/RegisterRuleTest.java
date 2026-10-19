package vn.t3nexus.apigateway.ratelimit;

import org.junit.jupiter.api.Test;
import vn.t3nexus.apigateway.support.GatewayTestBase;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * KT-01 và KT-02 (feature 06, nhóm A): luật đầu tiên theo cấu hình thật của cổng, {@code POST /register} ở host {@code auth}, 10 yêu cầu
 * mỗi giờ mỗi địa chỉ. Yêu cầu thứ 11 nhận 429 kèm {@code Retry-After} và thân {@code TOO_MANY_REQUESTS}; luật không chặn đường dẫn,
 * phương thức hay host khác.
 */
class RegisterRuleTest extends GatewayTestBase {

    @Test
    void the_eleventh_registration_from_one_address_gets_429_with_retry_after_and_the_common_error_body() {
        OAUTH2.respond("POST", "/register", 201, "application/json", "{\"success\":true}");

        for (int i = 1; i <= 10; i++) {
            post(AUTH_HOST, "/register").exchange().expectStatus().isCreated();
        }
        var denied = post(AUTH_HOST, "/register").exchange()
                .expectStatus().isEqualTo(429)
                .expectHeader().exists("Retry-After")
                .expectHeader().contentTypeCompatibleWith("application/json")
                .expectBody(String.class).returnResult();

        assertThat(denied.getResponseBody()).contains("\"success\":false", "\"code\":\"TOO_MANY_REQUESTS\"");
        long retryAfter = Long.parseLong(denied.getResponseHeaders().getFirst("Retry-After"));
        assertThat(retryAfter).as("còn lại đến khi giải phóng một lượt, không vượt cả giờ").isBetween(1L, 3600L);
        assertThat(OAUTH2.requests()).as("yêu cầu bị chặn không tới Xác thực").hasSize(10);
    }

    @Test
    void the_registration_rule_does_not_block_other_paths_methods_or_hosts() {
        OAUTH2.respond("POST", "/register", 201, "text/plain", "ok");
        OAUTH2.respond("POST", "/login", 200, "text/plain", "ok");
        OAUTH2.respond("GET", "/register", 200, "text/plain", "ok");
        OAUTH2.respond("POST", "/oauth2/token", 200, "text/plain", "ok");
        FRONTEND.respond("POST", "/register", 200, "text/plain", "giao-dien");
        for (int i = 0; i < 10; i++) post(AUTH_HOST, "/register").exchange().expectStatus().isCreated();
        post(AUTH_HOST, "/register").exchange().expectStatus().isEqualTo(429);

        post(AUTH_HOST, "/login").exchange().expectStatus().isOk();
        get(AUTH_HOST, "/register").exchange().expectStatus().isOk();
        post(AUTH_HOST, "/oauth2/token").exchange().expectStatus().isOk();
        post(SHOP_HOST, "/register").exchange().expectStatus().isOk();
    }
}
