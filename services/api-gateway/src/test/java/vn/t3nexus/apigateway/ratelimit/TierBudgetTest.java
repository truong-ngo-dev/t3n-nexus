package vn.t3nexus.apigateway.ratelimit;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import vn.t3nexus.apigateway.support.GatewayTestBase;

/**
 * KT-33 (feature 06, nhóm A, R5, `tech.md` T1): mỗi tầng chỉ đếm yêu cầu nó cho qua; yêu cầu bị luật đường dẫn chặn dừng ở luật và
 * không làm tăng bộ đếm chung. Luật {@code POST /register} mức 2, mức chung mức 5: sau 2 yêu cầu qua và 4 yêu cầu bị luật chặn, mức chung
 * mới tính 2, nên 3 yêu cầu {@code POST /login} nữa vẫn qua và yêu cầu thứ 4 mới nhận 429 (2 + 3 = 5). Nếu yêu cầu bị chặn cũng bị tính thì mức
 * chung đã cạn từ trước.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        // danh sách luật không gộp giữa các nguồn cấu hình của Boot: phải khai báo đủ luật, không chỉ trường muốn đổi
        "app.ratelimit.rules[0].name=register",
        "app.ratelimit.rules[0].host=auth",
        "app.ratelimit.rules[0].method=POST",
        "app.ratelimit.rules[0].path=/register",
        "app.ratelimit.rules[0].limit=2",
        "app.ratelimit.rules[0].window-seconds=3600",
        "app.ratelimit.default.limit=5",
        "app.ratelimit.default.window-seconds=60"
})
class TierBudgetTest extends GatewayTestBase {

    @Test
    void requests_blocked_by_the_path_rule_do_not_consume_the_general_budget() {
        OAUTH2.respond("POST", "/register", 201, "text/plain", "ok");
        OAUTH2.respond("POST", "/login", 200, "text/plain", "ok");

        post(AUTH_HOST, "/register").exchange().expectStatus().isCreated();
        post(AUTH_HOST, "/register").exchange().expectStatus().isCreated();
        for (int i = 0; i < 4; i++) post(AUTH_HOST, "/register").exchange().expectStatus().isEqualTo(429);

        for (int i = 0; i < 3; i++) post(AUTH_HOST, "/login").exchange().expectStatus().isOk();
        post(AUTH_HOST, "/login").exchange().expectStatus().isEqualTo(429);
    }
}
