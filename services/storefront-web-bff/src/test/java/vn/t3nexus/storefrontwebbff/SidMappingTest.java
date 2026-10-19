package vn.t3nexus.storefrontwebbff;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.session.ReactiveSessionRepository;
import org.springframework.session.Session;
import vn.t3nexus.storefrontwebbff.support.GatewayTestBase;
import vn.t3nexus.storefrontwebbff.support.RedisContainerHolder;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * KT-16, KT-38, KT-39 (feature 06, nhóm D, `tech.md` T6): đăng nhập xong BFF ghi ánh xạ hai chiều theo {@code sid} có hạn; token thiếu
 * {@code sid} thì không ánh xạ nhưng đăng nhập vẫn xong; callback lần hai với cùng {@code sid} (xác thực im lặng) ghi đè ánh xạ và xóa
 * phiên BFF cũ; mỗi lần lưu token mới đặt lại hạn phiên và gia hạn ánh xạ, hạn ánh xạ không nhỏ hơn hạn phiên. Hạn thử: phiên 600 giây,
 * ân hạn ánh xạ 100 giây. (Xoay mã CSRF của KT-38 kiểm ở nhóm B, bước 9.)
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "app.session.ttl=600s",
        "app.session.mapping-grace=100s"
})
class SidMappingTest extends GatewayTestBase {

    private static final String SID_KEY = "storefront:sid:";
    private static final String SESSION_KEY = "storefront:session:";

    @Autowired
    private ReactiveSessionRepository<? extends Session> sessions;

    @Test
    void login_writes_two_way_mapping_with_a_ttl_not_shorter_than_the_session() {
        String sid = UUID.randomUUID().toString();

        var login = login("user-1", sid, "USER");

        assertThat(login.status()).isEqualTo(302);
        String sessionId = RedisContainerHolder.get(SID_KEY + sid);
        assertThat(sessionId).isNotBlank().isEqualTo(login.sessionCookie());
        assertThat(RedisContainerHolder.get(SESSION_KEY + sessionId)).isEqualTo(sid);
        assertThat(sessions.findById(sessionId).block()).isNotNull();
        assertThat(RedisContainerHolder.keys("storefront:oauth:*")).as("không còn khóa tên cũ").isEmpty();

        assertThat(sessions.findById(sessionId).block().getMaxInactiveInterval()).isEqualTo(Duration.ofSeconds(600));
        assertThat(RedisContainerHolder.ttl(SID_KEY + sid)).isBetween(650L, 700L);
        assertThat(RedisContainerHolder.ttl(SESSION_KEY + sessionId)).isBetween(650L, 700L);
    }

    @Test
    void token_without_sid_still_logs_in_but_creates_no_mapping() {
        var login = login("user-1", null, "USER");

        assertThat(login.status()).isEqualTo(302);
        assertThat(RedisContainerHolder.keys("storefront:sid:*")).isEmpty();
        assertThat(RedisContainerHolder.keys("storefront:session:*")).isEmpty();
    }

    @Test
    void second_callback_with_the_same_sid_replaces_the_mapping_and_deletes_the_older_bff_session() {
        String sid = UUID.randomUUID().toString();
        var first = login("user-1", sid, "USER");
        var second = login("user-1", sid, "USER");

        assertThat(second.sessionCookie()).isNotEqualTo(first.sessionCookie());
        assertThat(RedisContainerHolder.get(SID_KEY + sid)).isEqualTo(second.sessionCookie());
        assertThat(RedisContainerHolder.get(SESSION_KEY + second.sessionCookie())).isEqualTo(sid);
        assertThat(RedisContainerHolder.exists(SESSION_KEY + first.sessionCookie())).as("khóa ngược của phiên cũ").isFalse();
        assertThat(sessions.findById(first.sessionCookie()).block()).as("phiên BFF cũ").isNull();
        assertThat(sessions.findById(second.sessionCookie()).block()).isNotNull();
        assertThat(RedisContainerHolder.keys("storefront:sid:*")).hasSize(1);

        client().get().uri("/bff/auth/session").header("Cookie", "STOREFRONT_SESSION=" + second.sessionCookie())
                .exchange().expectStatus().isOk();
        client().get().uri("/bff/auth/session").header("Cookie", "STOREFRONT_SESSION=" + first.sessionCookie())
                .exchange().expectStatus().isUnauthorized();
    }

    @Test
    void each_new_token_save_resets_the_session_ttl_and_renews_the_mapping() {
        String sid = UUID.randomUUID().toString();
        var first = login("user-1", sid, "USER");
        RedisContainerHolder.expire(SID_KEY + sid, 30);
        RedisContainerHolder.expire(SESSION_KEY + first.sessionCookie(), 30);
        assertThat(RedisContainerHolder.ttl(SID_KEY + sid)).isLessThanOrEqualTo(30L);

        var second = login("user-1", sid, "USER");

        assertThat(RedisContainerHolder.ttl(SID_KEY + sid)).isBetween(650L, 700L);
        assertThat(RedisContainerHolder.ttl(SESSION_KEY + second.sessionCookie())).isBetween(650L, 700L);
        assertThat(sessions.findById(second.sessionCookie()).block().getMaxInactiveInterval()).isEqualTo(Duration.ofSeconds(600));
    }
}
