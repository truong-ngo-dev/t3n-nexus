package vn.t3nexus.apigateway.ratelimit;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import vn.t3nexus.apigateway.support.GatewayTestBase;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * KT-05 (feature 06, nhóm A, R1): Redis không với tới được thì lớp giới hạn mở cửa (yêu cầu vẫn qua, kể cả quá mức) và không làm treo yêu
 * cầu: lệnh đếm có thời gian chờ ngắn. Trường hợp Redis nhận kết nối nhưng im lặng kiểm ở bộ test của thư viện
 * ({@code ReactiveRedisRateLimiterTest}).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "app.ratelimit.redis-timeout-ms=300",
        // danh sách luật không gộp giữa các nguồn cấu hình của Boot: phải khai báo đủ luật, không chỉ trường muốn đổi
        "app.ratelimit.rules[0].name=register",
        "app.ratelimit.rules[0].host=auth",
        "app.ratelimit.rules[0].method=POST",
        "app.ratelimit.rules[0].path=/register",
        "app.ratelimit.rules[0].limit=1",
        "app.ratelimit.rules[0].window-seconds=3600",
        "spring.data.redis.connect-timeout=500ms"
})
class RedisOutageTest extends GatewayTestBase {

    @BeforeAll
    static void redisIsDown() {
        System.setProperty(REDIS_PORT_OVERRIDE, "1");   // cổng không có gì nghe
    }

    @AfterAll
    static void restoreRedisPort() {
        System.clearProperty(REDIS_PORT_OVERRIDE);
    }

    @Test
    void requests_pass_even_beyond_the_limit_and_are_not_held_up_when_redis_is_down() {
        OAUTH2.respond("POST", "/register", 201, "text/plain", "ok");

        for (int i = 0; i < 3; i++) {
            long start = System.nanoTime();
            post(AUTH_HOST, "/register").exchange().expectStatus().isCreated();
            assertThat((System.nanoTime() - start) / 1_000_000).as("lần %d", i + 1).isLessThan(2_500);
        }
        assertThat(OAUTH2.requests()).hasSize(3);
    }
}
