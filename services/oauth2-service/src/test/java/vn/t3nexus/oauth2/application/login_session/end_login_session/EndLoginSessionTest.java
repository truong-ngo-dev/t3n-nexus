package vn.t3nexus.oauth2.application.login_session.end_login_session;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.t3nexus.oauth2.application.login_session.LoginTestFixture;
import vn.t3nexus.oauth2.application.login_session.open_login_session.OpenLoginSession;
import vn.t3nexus.oauth2.application.login_session.record_client_session_issued.RecordClientSessionIssued;
import vn.t3nexus.oauth2.domain.login_session.EndReason;
import vn.t3nexus.oauth2.domain.login_session.LoginSession;
import vn.t3nexus.oauth2.domain.login_session.LoginSessionId;
import vn.t3nexus.oauth2.domain.login_session.SessionEndedEvent;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static vn.t3nexus.oauth2.application.login_session.LoginTestFixture.NOW;
import static vn.t3nexus.oauth2.application.login_session.LoginTestFixture.USER_ID;

/** Tự đăng xuất trên cổng giả: KT-01, 03, 04, 06, 19, 22, 23, 24 (phần use case). */
class EndLoginSessionTest {

    private static final String SID_A_SHOP   = "01938a5e-1c00-7000-8000-0000000000a1";
    private static final String SID_A_SELLER = "01938a5e-1c00-7000-8000-0000000000a2";
    private static final String SID_B_SHOP   = "01938a5e-1c00-7000-8000-0000000000b1";

    private LoginTestFixture fx;
    private EndLoginSession endLoginSession;
    private String loginSessionA;
    private String loginSessionB;

    @BeforeEach
    void setUp() {
        fx = new LoginTestFixture();
        endLoginSession = new EndLoginSession(fx.sessionRepository, fx.clock);
        // thiết bị A: hai ứng dụng; thiết bị B: một ứng dụng; cùng một tài khoản
        loginSessionA = open("IDP-A", "device-a");
        loginSessionB = open("IDP-B", "device-b");
        record(loginSessionA, "shop-web", SID_A_SHOP, "auth-A1");
        record(loginSessionA, "seller-web", SID_A_SELLER, "auth-A2");
        record(loginSessionB, "shop-web", SID_B_SHOP, "auth-B1");
        fx.sessions.values().forEach(LoginSession::clearDomainEvents);
    }

    private String open(String idpSessionId, String device) {
        return new OpenLoginSession(fx.accountRepository, fx.sessionRepository, fx.deviceKeyHasher, fx.lifetimePolicy,
                fx.ids, fx.clock)
                .handle(new OpenLoginSession.Command(USER_ID.toString(), device, "203.0.113.7", "Mozilla/5.0", idpSessionId))
                .loginSessionId();
    }

    private void record(String loginSessionId, String clientId, String sid, String authorizationId) {
        new RecordClientSessionIssued(fx.sessionRepository, fx.clock).handle(
                new RecordClientSessionIssued.Command(loginSessionId, clientId, sid, authorizationId, "jti-" + sid));
    }

    private LoginSession session(String loginSessionId) {
        return fx.sessions.get(LoginSessionId.of(loginSessionId));
    }

    // ───────────── KT-01, KT-24 ─────────────

    @Test
    void it_ends_the_login_session_and_every_client_session_and_reports_each_one() {
        EndLoginSession.Result result = endLoginSession.handle(new EndLoginSession.Command("IDP-A", SID_A_SHOP));

        assertThat(result.ended()).isTrue();
        assertThat(result.loginSessionId()).isEqualTo(loginSessionA);
        assertThat(session(loginSessionA)).isNull();
        // đủ để xóa bản ghi ủy quyền và báo ứng dụng (R6): lấy ra trước khi `end` xóa danh sách phiên ứng dụng
        assertThat(result.endedClientSessions()).containsExactlyInAnyOrder(
                new EndLoginSession.EndedClientSession(SID_A_SHOP, "shop-web", "auth-A1"),
                new EndLoginSession.EndedClientSession(SID_A_SELLER, "seller-web", "auth-A2"));
    }

    // ───────────── KT-04 ─────────────

    @Test
    void it_leaves_the_session_of_another_device_of_the_same_account_untouched() {
        endLoginSession.handle(new EndLoginSession.Command("IDP-A", SID_A_SHOP));

        assertThat(session(loginSessionB)).isNotNull();
        assertThat(session(loginSessionB).clientSessions()).hasSize(1);
    }

    // ───────────── KT-06 ─────────────

    @Test
    void it_records_one_ended_event_with_the_reason_and_no_internal_identifiers_and_a_second_logout_adds_none() {
        LoginSession a = session(loginSessionA);

        endLoginSession.handle(new EndLoginSession.Command("IDP-A", SID_A_SHOP));
        EndLoginSession.Result again = endLoginSession.handle(new EndLoginSession.Command("IDP-A", SID_A_SHOP));

        assertThat(again.ended()).isFalse();
        assertThat(a.getDomainEvents()).singleElement().isInstanceOfSatisfying(SessionEndedEvent.class, e -> {
            assertThat(e.getPayload().toString()).contains(EndReason.SELF_LOGOUT.name())
                    .doesNotContain("IDP-A").doesNotContain("auth-A1").doesNotContain("auth-A2");
        });
    }

    // ───────────── KT-03 (phần use case) ─────────────

    @Test
    void a_cookie_that_matches_no_living_session_ends_nothing_and_is_not_an_error() {
        EndLoginSession.Result result = endLoginSession.handle(new EndLoginSession.Command("IDP-UNKNOWN", SID_A_SHOP));

        assertThat(result.ended()).isFalse();
        assertThat(result.endedClientSessions()).isEmpty();
        assertThat(fx.sessions).hasSize(2);
    }

    @Test
    void an_expired_session_ends_nothing_and_records_no_event() {
        LoginSession a = session(loginSessionA);
        EndLoginSession later = new EndLoginSession(fx.sessionRepository,
                Clock.fixed(NOW.plus(Duration.ofDays(31)), ZoneOffset.UTC));

        EndLoginSession.Result result = later.handle(new EndLoginSession.Command("IDP-A", SID_A_SHOP));

        assertThat(result.ended()).isFalse();
        assertThat(a.getDomainEvents()).isEmpty();
        assertThat(session(loginSessionA)).isNotNull();       // hàng quá hạn do job dọn lo, không phải đăng xuất
    }

    // ───────────── KT-23 (không cookie) ─────────────

    @Test
    void without_a_cookie_nothing_is_ended_even_with_a_valid_hint() {
        for (String cookie : new String[]{null, "", "  "}) {
            assertThat(endLoginSession.handle(new EndLoginSession.Command(cookie, SID_A_SHOP)).ended()).isFalse();
        }

        assertThat(fx.sessions).hasSize(2);
        assertThat(session(loginSessionA).clientSessions()).hasSize(2);
    }

    // ───────────── KT-19 ─────────────

    @Test
    void a_hint_that_belongs_to_another_session_ends_nothing() {
        // cookie của A nhưng hint của phiên ứng dụng thuộc B
        EndLoginSession.Result result = endLoginSession.handle(new EndLoginSession.Command("IDP-A", SID_B_SHOP));

        assertThat(result.ended()).isFalse();
        assertThat(fx.sessions).hasSize(2);
        assertThat(session(loginSessionA).clientSessions()).hasSize(2);
        assertThat(session(loginSessionA).getDomainEvents()).isEmpty();
    }

    @Test
    void a_missing_or_malformed_hint_ends_nothing() {
        for (String hint : new String[]{null, "", "not-a-uuid"}) {
            assertThat(endLoginSession.handle(new EndLoginSession.Command("IDP-A", hint)).ended()).isFalse();
        }

        assertThat(fx.sessions).hasSize(2);
    }
}
