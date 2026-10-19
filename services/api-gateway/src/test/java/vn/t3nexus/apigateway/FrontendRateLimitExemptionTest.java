package vn.t3nexus.apigateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import vn.t3nexus.apigateway.support.GatewayTestBase;
import vn.t3nexus.apigateway.support.RedisContainerHolder;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phát hiện khi chạy giao diện thật qua cổng (feature 06, nhóm E): máy chủ phát triển của giao diện phục vụ hàng trăm tệp mô-đun cho
 * một lần mở trang, nên mức chung 300 yêu cầu mỗi 60 giây làm {@code main.js} nhận 429 và ứng dụng không khởi động được. Mức chung
 * nhằm bảo vệ API và Xác thực, không phải tài nguyên tĩnh của giao diện (môi trường thật phục vụ bằng CDN hoặc máy chủ tĩnh), nên
 * tuyến tới máy chủ giao diện <b>không bị đếm</b> và cũng không làm cạn bộ đếm dành cho tuyến khác.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "app.ratelimit.default.limit=2",
        "app.ratelimit.default.window-seconds=60"
})
class FrontendRateLimitExemptionTest extends GatewayTestBase {

    @Test
    void requests_to_the_frontend_server_are_not_limited_and_do_not_consume_the_budget_of_other_routes() {
        FRONTEND.respond("GET", "/main.js", 200, "text/javascript", "// app");
        OAUTH2.respond("GET", "/login", 200, "text/plain", "ok");

        for (int i = 0; i < 6; i++) {
            get(SHOP_HOST, "/main.js").exchange().expectStatus().isOk();
        }
        assertThat(RedisContainerHolder.keys("ratelimit:*")).as("yêu cầu tới máy chủ giao diện không tạo bộ đếm").isEmpty();

        get(AUTH_HOST, "/login").exchange().expectStatus().isOk();
        get(AUTH_HOST, "/login").exchange().expectStatus().isOk();
        get(AUTH_HOST, "/login").exchange().expectStatus().isEqualTo(429);
    }
}
