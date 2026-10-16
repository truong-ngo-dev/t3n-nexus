package vn.t3nexus.oauth2.application.login_session.open_login_session;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.t3nexus.oauth2.application.login_session.LoginTestFixture;
import vn.t3nexus.oauth2.domain.login_session.DeviceKeyHash;
import vn.t3nexus.oauth2.domain.login_session.LoginSession;
import vn.t3nexus.oauth2.domain.login_session.SessionIssuedEvent;
import vn.t3nexus.oauth2.domain.user_account.Role;
import vn.t3nexus.oauth2.domain.user_account.UserAccountErrorCode;
import vn.t3nexus.oauth2.domain.user_account.UserAccountException;
import vn.t3nexus.oauth2.domain.user_account.UserAccountId;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static vn.t3nexus.oauth2.application.login_session.LoginTestFixture.ADMIN_ID;
import static vn.t3nexus.oauth2.application.login_session.LoginTestFixture.NOW;
import static vn.t3nexus.oauth2.application.login_session.LoginTestFixture.PENDING_ID;
import static vn.t3nexus.oauth2.application.login_session.LoginTestFixture.USER_ID;

/** Nửa mở phiên của đăng nhập trên cổng giả: KT-01, 18, 26 (phần use case). */
class OpenLoginSessionTest {

    private LoginTestFixture fx;
    private OpenLoginSession open;

    @BeforeEach
    void setUp() {
        fx = new LoginTestFixture();
        open = new OpenLoginSession(fx.accountRepository, fx.sessionRepository, fx.deviceKeyHasher, fx.lifetimePolicy,
                fx.ids, fx.clock);
    }

    private OpenLoginSession.Command command(UUID accountId) {
        return new OpenLoginSession.Command(accountId.toString(), "raw-device-cookie", "203.0.113.7",
                "Mozilla/5.0 Chrome/130", "IDP-SESSION-VALUE-1");
    }

    @Test
    void opens_exactly_one_user_session_without_client_sessions() {
        OpenLoginSession.Result result = open.handle(command(USER_ID));

        assertThat(fx.sessions).hasSize(1);
        LoginSession session = fx.sessions.values().iterator().next();
        assertThat(session.getId().getValueAsString()).isEqualTo(result.loginSessionId());
        assertThat(session.userAccountId()).isEqualTo(UserAccountId.of(USER_ID));
        assertThat(session.clientSessions()).isEmpty();
        assertThat(session.issuedAt()).isEqualTo(NOW);
        assertThat(session.idpSessionId()).isEqualTo("IDP-SESSION-VALUE-1");
        assertThat(result.userAccountId()).isEqualTo(USER_ID.toString());
        assertThat(result.role()).isEqualTo(Role.USER);
        assertThat(result.expiresAt()).isEqualTo(session.expiresAt());
    }

    @Test
    void the_session_expiry_follows_the_role_of_the_account() {
        open.handle(command(USER_ID));
        open.handle(command(ADMIN_ID));

        assertThat(fx.sessions.values()).extracting(LoginSession::expiresAt)
                .containsExactlyInAnyOrder(NOW.plus(Duration.ofDays(30)), NOW.plus(Duration.ofHours(10)));
    }

    @Test
    void the_session_issued_event_carries_the_hashed_device_key_and_context_never_the_raw_values() {
        open.handle(command(USER_ID));

        LoginSession session = fx.sessions.values().iterator().next();
        assertThat(session.deviceKey()).isEqualTo(new DeviceKeyHash("h(raw-device-cookie)"));
        assertThat(session.getDomainEvents()).hasSize(1);
        SessionIssuedEvent event = (SessionIssuedEvent) session.getDomainEvents().iterator().next();
        String payload = event.getPayload().toString();
        assertThat(payload).contains("h(raw-device-cookie)", "203.0.113.7", "Mozilla/5.0 Chrome/130");
        assertThat(payload).doesNotContain("IDP-SESSION-VALUE-1");
        assertThat(payload.replace("h(raw-device-cookie)", "")).doesNotContain("raw-device-cookie");
    }

    @Test
    void a_missing_device_cookie_still_opens_a_session_with_no_device_key() {
        open.handle(new OpenLoginSession.Command(USER_ID.toString(), null, "203.0.113.7", "Mozilla/5.0", "IDP-1"));

        assertThat(fx.sessions.values().iterator().next().deviceKey()).isNull();
    }

    @Test
    void every_call_opens_a_new_session_so_a_second_device_gets_its_own() {
        OpenLoginSession.Result first  = open.handle(command(USER_ID));
        OpenLoginSession.Result second = open.handle(command(USER_ID));

        assertThat(first.loginSessionId()).isNotEqualTo(second.loginSessionId());
        assertThat(fx.sessions).hasSize(2);
    }

    @Test
    void an_account_that_is_not_active_cannot_open_a_session() {
        Throwable thrown = catchThrowable(() -> open.handle(command(PENDING_ID)));

        assertThat(thrown).isInstanceOfSatisfying(UserAccountException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(UserAccountErrorCode.ACCOUNT_NOT_ACTIVE));
        assertThat(fx.sessions).isEmpty();
    }

    @Test
    void an_unknown_account_cannot_open_a_session() {
        Throwable thrown = catchThrowable(() -> open.handle(command(UUID.fromString("01938a5e-1c00-7000-8000-0000000fffff"))));

        assertThat(thrown).isInstanceOfSatisfying(UserAccountException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(UserAccountErrorCode.CREDENTIAL_NOT_FOUND));
        assertThat(fx.sessions).isEmpty();
    }
}
