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
 * KT-20 (feature 06, nhóm D, R16): đăng xuất ở BFF xóa phiên và ánh xạ theo {@code sid} của người dùng, trả 202 kèm {@code Location} tới
 * {@code connect/logout} ở host {@code auth} với {@code id_token_hint} và {@code post_logout_redirect_uri} ở host {@code shop}.
 */
class LogoutTest extends GatewayTestBase {

    @Autowired
    private ReactiveSessionRepository<? extends Session> sessions;

    @Test
    void logout_removes_the_mapping_by_sid_and_answers_202_with_the_end_session_location() {
        String sid = UUID.randomUUID().toString();
        var login = login("user-1", sid, "USER");
        assertThat(RedisContainerHolder.exists("storefront:sid:" + sid)).isTrue();

        var result = client().post().uri("/bff/auth/logout").header("Cookie", login.cookieHeader()).header("X-XSRF-TOKEN", login.xsrfCookie())
                .exchange().expectStatus().isAccepted().returnResult(Void.class);

        String location = result.getResponseHeaders().getFirst("Location");
        assertThat(location).startsWith("http://auth.localhost:8000/connect/logout?id_token_hint=")
                .contains("post_logout_redirect_uri=http://shop.localhost:8000/");
        assertThat(RedisContainerHolder.exists("storefront:sid:" + sid)).isFalse();
        assertThat(RedisContainerHolder.exists("storefront:session:" + login.sessionCookie())).isFalse();
        assertThat(sessions.findById(login.sessionCookie()).block()).isNull();
        assertThat(RedisContainerHolder.sessionsHoldingTokens()).as("không còn phiên nào giữ token trong Redis, dưới bất kỳ mã nào").isZero();
    }
}
