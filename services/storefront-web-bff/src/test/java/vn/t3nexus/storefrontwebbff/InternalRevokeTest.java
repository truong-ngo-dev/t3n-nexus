package vn.t3nexus.storefrontwebbff;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.session.ReactiveSessionRepository;
import org.springframework.session.Session;
import org.springframework.test.web.reactive.server.WebTestClient;
import vn.t3nexus.storefrontwebbff.support.GatewayTestBase;
import vn.t3nexus.storefrontwebbff.support.RedisContainerHolder;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * KT-18, KT-19, KT-23 (feature 06, nhóm D, R14, R15, R18): nhận báo đăng xuất {@code {"sid"}} từ Xác thực. Cần token dịch vụ phạm vi
 * {@code storefront.internal} (thiếu 401, sai phạm vi 403); {@code sid} không phải UUID thì 400; có ánh xạ thì xóa phiên và hai khóa,
 * lặp lại hoặc không có ánh xạ vẫn 200; cookie cũ sau đó nhận 401. Bộ giải mã token dịch vụ chấp nhận lệch đồng hồ 5 giây.
 */
class InternalRevokeTest extends GatewayTestBase {

    @Autowired
    private ReactiveSessionRepository<? extends Session> sessions;

    private String serviceToken(String scope, Instant expiresAt) {
        return IDP.jwt(Map.of("sub", "oauth2-service-internal", "scope", scope), expiresAt);
    }

    private WebTestClient.ResponseSpec revoke(String token, String sid) {
        var request = client().post().uri("/bff/internal/sessions/revoke").header("Content-Type", "application/json");
        if (token != null) request.header("Authorization", "Bearer " + token);
        return request.bodyValue("{\"sid\":\"" + sid + "\"}").exchange();
    }

    @Test
    void revoke_deletes_the_session_and_both_mapping_keys_and_the_old_cookie_is_then_401() {
        String sid = UUID.randomUUID().toString();
        var login = login("user-1", sid, "USER");
        client().get().uri("/bff/auth/session").header("Cookie", "STOREFRONT_SESSION=" + login.sessionCookie()).exchange().expectStatus().isOk();

        revoke(serviceToken("storefront.internal", Instant.now().plusSeconds(60)), sid).expectStatus().isOk();

        assertThat(RedisContainerHolder.exists("storefront:sid:" + sid)).isFalse();
        assertThat(RedisContainerHolder.exists("storefront:session:" + login.sessionCookie())).isFalse();
        assertThat(sessions.findById(login.sessionCookie()).block()).isNull();
        assertThat(RedisContainerHolder.sessionsHoldingTokens()).as("không còn phiên giữ token dưới mã nào").isZero();
        client().get().uri("/bff/auth/session").header("Cookie", "STOREFRONT_SESSION=" + login.sessionCookie()).exchange().expectStatus().isUnauthorized();
    }

    @Test
    void repeated_or_unmapped_revoke_is_still_200() {
        String sid = UUID.randomUUID().toString();
        login("user-1", sid, "USER");
        String token = serviceToken("storefront.internal", Instant.now().plusSeconds(60));

        revoke(token, sid).expectStatus().isOk();
        revoke(token, sid).expectStatus().isOk();
        revoke(token, UUID.randomUUID().toString()).expectStatus().isOk();
    }

    @Test
    void sid_that_is_not_a_uuid_is_400_and_missing_or_wrong_scope_is_401_or_403() {
        String good = serviceToken("storefront.internal", Instant.now().plusSeconds(60));

        revoke(good, "not-a-uuid").expectStatus().isBadRequest();
        revoke(null, UUID.randomUUID().toString()).expectStatus().isUnauthorized();
        revoke(serviceToken("other.scope", Instant.now().plusSeconds(60)), UUID.randomUUID().toString()).expectStatus().isForbidden();
    }

    @Test
    void service_token_tolerates_5_seconds_of_clock_skew_but_not_10() {
        String sid = UUID.randomUUID().toString();

        revoke(serviceToken("storefront.internal", Instant.now().minusSeconds(3)), sid).expectStatus().isOk();
        revoke(serviceToken("storefront.internal", Instant.now().minusSeconds(10)), sid).expectStatus().isUnauthorized();
    }

    /** Dòng log phải nói đúng điều đã xảy ra: xóa được thì chỉ có "cleared", không có "no mapping" (từng ghi cả hai vì {@code Mono<Void>} luôn rỗng). */
    @Test
    void the_log_says_cleared_when_a_mapping_was_removed_and_no_mapping_only_when_there_was_none() {
        var logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(vn.t3nexus.storefrontwebbff.presentation.SessionRevokeController.class);
        var appender = new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        try {
            String sid = UUID.randomUUID().toString();
            login("user-1", sid, "USER");
            String token = serviceToken("storefront.internal", Instant.now().plusSeconds(60));

            revoke(token, sid).expectStatus().isOk();
            assertThat(appender.list).extracting(e -> e.getFormattedMessage()).anyMatch(m -> m.contains("cleared")).noneMatch(m -> m.contains("no mapping"));

            appender.list.clear();
            revoke(token, sid).expectStatus().isOk();
            assertThat(appender.list).extracting(e -> e.getFormattedMessage()).anyMatch(m -> m.contains("no mapping")).noneMatch(m -> m.contains("cleared"));
        } finally {
            logger.detachAppender(appender);
        }
    }
}
