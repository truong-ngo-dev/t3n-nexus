package vn.t3nexus.apigateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import vn.t3nexus.apigateway.support.GatewayTestBase;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Bộ khung test của cổng chạy đúng (feature 06, bước 4): cổng thật, Redis thật, dịch vụ sau giả. Ca mức chung theo địa chỉ mạng đặc tả
 * hành vi hiện có để có chỗ so khi nhóm A (feature 06) viết lại giới hạn tần suất; test của nhóm E ở {@code HostRoutingTest},
 * {@code ForwardedHeadersTest}, {@code GatewayCorsTest}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "app.ratelimit.default.limit=2",
        "app.ratelimit.default.window-seconds=60"
})
class GatewayFrameworkTest extends GatewayTestBase {

    @Test
    void the_general_per_address_limit_uses_the_real_redis_and_returns_429_with_retry_after() {
        OAUTH2.respond("GET", "/login", 200, "text/plain", "ok");

        get(AUTH_HOST, "/login").exchange().expectStatus().isOk();
        get(AUTH_HOST, "/login").exchange().expectStatus().isOk();
        get(AUTH_HOST, "/login").exchange()
                .expectStatus().isEqualTo(429)
                .expectHeader().exists("Retry-After")
                .expectBody(String.class).value(body -> assertThat(body).contains("\"code\":\"TOO_MANY_REQUESTS\""));   // KT-06: mức chung cũng có thân chuẩn

        assertThat(OAUTH2.requests()).as("yêu cầu bị chặn không tới dịch vụ sau").hasSize(2);
    }
}
