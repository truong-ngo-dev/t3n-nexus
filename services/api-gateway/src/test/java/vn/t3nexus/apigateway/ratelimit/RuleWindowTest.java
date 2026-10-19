package vn.t3nexus.apigateway.ratelimit;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import vn.t3nexus.apigateway.support.GatewayTestBase;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * KT-03 (feature 06, nhóm A, R7): hết cửa sổ thì đăng ký lại được và {@code Retry-After} là số giây còn lại của cửa sổ chứ không phải độ
 * dài cả cửa sổ. Dùng cửa sổ ngắn (3 giây, 2 yêu cầu) để chạy được trong test.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        // danh sách luật không gộp giữa các nguồn cấu hình của Boot: phải khai báo đủ luật, không chỉ trường muốn đổi
        "app.ratelimit.rules[0].name=register",
        "app.ratelimit.rules[0].host=auth",
        "app.ratelimit.rules[0].method=POST",
        "app.ratelimit.rules[0].path=/register",
        "app.ratelimit.rules[0].limit=2",
        "app.ratelimit.rules[0].window-seconds=3"
})
class RuleWindowTest extends GatewayTestBase {

    @Test
    void retry_after_shrinks_with_time_and_the_route_opens_again_when_the_window_has_passed() throws Exception {
        OAUTH2.respond("POST", "/register", 201, "text/plain", "ok");
        post(AUTH_HOST, "/register").exchange().expectStatus().isCreated();
        post(AUTH_HOST, "/register").exchange().expectStatus().isCreated();

        long first = retryAfterOfDeniedRequest();
        assertThat(first).isBetween(1L, 3L);

        Thread.sleep(1_200);
        long second = retryAfterOfDeniedRequest();
        assertThat(second).as("giảm theo thời gian đã trôi").isLessThan(first);

        Thread.sleep(3_200);
        post(AUTH_HOST, "/register").exchange().expectStatus().isCreated();
    }

    private long retryAfterOfDeniedRequest() {
        var denied = post(AUTH_HOST, "/register").exchange().expectStatus().isEqualTo(429).returnResult(Void.class);
        return Long.parseLong(denied.getResponseHeaders().getFirst("Retry-After"));
    }
}
