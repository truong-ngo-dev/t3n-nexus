package vn.t3nexus.oauth2.application.login_session.resolve_authorization_session;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.t3nexus.oauth2.application.login_session.LoginTestFixture;
import vn.t3nexus.oauth2.application.login_session.open_login_session.OpenLoginSession;
import vn.t3nexus.oauth2.domain.login_session.ClientId;
import vn.t3nexus.oauth2.domain.login_session.ClientSessionId;
import vn.t3nexus.oauth2.domain.login_session.EndReason;
import vn.t3nexus.oauth2.domain.login_session.LoginSession;
import vn.t3nexus.oauth2.domain.login_session.LoginSessionErrorCode;
import vn.t3nexus.oauth2.domain.login_session.LoginSessionException;
import vn.t3nexus.oauth2.domain.login_session.LoginSessionId;
import vn.t3nexus.oauth2.domain.login_session.LoginSessionRepository;
import vn.t3nexus.oauth2.domain.user_account.Role;
import vn.t3nexus.oauth2.domain.user_account.UserAccountErrorCode;
import vn.t3nexus.oauth2.domain.user_account.UserAccountException;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static vn.t3nexus.oauth2.application.login_session.LoginTestFixture.NOW;
import static vn.t3nexus.oauth2.application.login_session.LoginTestFixture.PENDING_ID;
import static vn.t3nexus.oauth2.application.login_session.LoginTestFixture.USER_ID;

/** Cửa kiểm hạn ở `/oauth2/authorize` trên cổng giả: KT-23, 24 (phần use case) và việc chốt `sid` lúc cấp mã. */
class ResolveAuthorizationSessionTest {

    private LoginTestFixture fx;
    private OpenLoginSession open;
    private String loginSessionId;

    @BeforeEach
    void setUp() {
        fx = new LoginTestFixture();
        open = new OpenLoginSession(fx.accountRepository, fx.sessionRepository, fx.deviceKeyHasher, fx.lifetimePolicy,
                fx.ids, fx.clock);
        loginSessionId = open.handle(new OpenLoginSession.Command(USER_ID.toString(), "raw-device-cookie",
                "203.0.113.7", "Mozilla/5.0", "IDP-1")).loginSessionId();
    }

    private ResolveAuthorizationSession gate() {
        return gate(fx.clock, fx.sessionRepository);
    }

    private ResolveAuthorizationSession gate(Clock clock, LoginSessionRepository sessionRepository) {
        return new ResolveAuthorizationSession(sessionRepository, fx.accountRepository, fx.ids, clock);
    }

    private ResolveAuthorizationSession.Command command(String idpSessionId, String clientId) {
        return new ResolveAuthorizationSession.Command(loginSessionId, idpSessionId, clientId);
    }

    @Test
    void a_live_session_with_no_client_session_gets_a_freshly_chosen_sid_and_the_values_for_the_token() {
        ResolveAuthorizationSession.Result result = gate().handle(command("IDP-1", "shop-web"));

        LoginSession session = fx.sessions.get(LoginSessionId.of(loginSessionId));
        assertThat(result.loginSessionId()).isEqualTo(loginSessionId);
        assertThat(result.sid()).isNotBlank();
        assertThat(ClientSessionId.of(result.sid())).isNotNull();
        assertThat(result.expiresAt()).isEqualTo(session.expiresAt()).isEqualTo(NOW.plus(Duration.ofDays(30)));
        assertThat(result.role()).isEqualTo(Role.USER);
        assertThat(result.deviceKey()).isEqualTo("h(raw-device-cookie)");
        assertThat(session.clientSessions()).isEmpty();          // chỉ chốt mã, chưa tạo phiên ứng dụng
    }

    @Test
    void two_authorize_calls_for_a_client_with_no_session_choose_different_sids_until_one_is_issued() {
        String first  = gate().handle(command("IDP-1", "shop-web")).sid();
        String second = gate().handle(command("IDP-1", "shop-web")).sid();

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void a_client_that_already_has_a_session_reuses_its_sid() {
        ClientSessionId existing = ClientSessionId.of(UUID.fromString("01938a5e-1c00-7000-8000-0000000000e1"));
        LoginSession session = fx.sessions.get(LoginSessionId.of(loginSessionId));
        session.issueClientSession(new ClientId("shop-web"), existing, "auth-A", "jti-1", NOW);
        fx.sessionRepository.save(session);

        ResolveAuthorizationSession.Result sameClient  = gate().handle(command("IDP-1", "shop-web"));
        ResolveAuthorizationSession.Result otherClient = gate().handle(command("IDP-1", "seller-web"));

        assertThat(sameClient.sid()).isEqualTo(existing.getValueAsString());
        assertThat(otherClient.sid()).isNotEqualTo(existing.getValueAsString());
    }

    @Test
    void a_changed_spring_session_id_is_written_back_to_the_session() {
        // Kho giả giữ cùng một đối tượng nên đọc lại không đủ chứng minh: phải thấy lệnh lưu được gọi với giá trị mới.
        java.util.List<String> savedIdpIds = new java.util.ArrayList<>();
        LoginSessionRepository watching = new LoginSessionRepositoryDelegate(fx.sessionRepository) {
            @Override public void save(LoginSession session) { savedIdpIds.add(session.idpSessionId()); super.save(session); }
        };

        gate(fx.clock, watching).handle(command("IDP-ROTATED", "shop-web"));

        assertThat(savedIdpIds).containsExactly("IDP-ROTATED");
        assertThat(fx.sessions.get(LoginSessionId.of(loginSessionId)).idpSessionId()).isEqualTo("IDP-ROTATED");
    }

    @Test
    void an_unchanged_spring_session_id_writes_nothing() {
        boolean[] saved = {false};
        LoginSessionRepository watching = new LoginSessionRepositoryDelegate(fx.sessionRepository) {
            @Override public void save(LoginSession session) { saved[0] = true; super.save(session); }
        };

        gate(fx.clock, watching).handle(command("IDP-1", "shop-web"));

        assertThat(saved[0]).isFalse();
    }

    // ───────────── Từ chối (KT-23) ─────────────

    @Test
    void a_session_that_no_longer_exists_is_rejected_even_though_the_cookie_is_alive() {
        fx.sessions.clear();

        Throwable thrown = catchThrowable(() -> gate().handle(command("IDP-1", "shop-web")));

        assertThat(thrown).isInstanceOfSatisfying(LoginSessionException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(LoginSessionErrorCode.SESSION_EXPIRED));
    }

    @Test
    void a_session_whose_expiry_has_arrived_is_rejected() {
        Clock atExpiry = Clock.fixed(NOW.plus(Duration.ofDays(30)), ZoneOffset.UTC);

        Throwable thrown = catchThrowable(() -> gate(atExpiry, fx.sessionRepository).handle(command("IDP-1", "shop-web")));

        assertThat(thrown).isInstanceOfSatisfying(LoginSessionException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(LoginSessionErrorCode.SESSION_EXPIRED));
    }

    @Test
    void an_ended_session_is_rejected() {
        LoginSession session = fx.sessions.get(LoginSessionId.of(loginSessionId));
        session.end(EndReason.SELF_LOGOUT, NOW);
        fx.sessionRepository.save(session);

        Throwable thrown = catchThrowable(() -> gate().handle(command("IDP-1", "shop-web")));

        assertThat(thrown).isInstanceOf(LoginSessionException.class);
    }

    @Test
    void an_account_that_is_no_longer_active_gets_no_code() {
        // phiên của tài khoản đang chờ kích hoạt (giả lập bị khóa sau lúc mở phiên)
        LoginSession forPending = LoginSession.open(LoginSessionId.of(UUID.fromString("01938a5e-1c00-7000-8000-0000000000f1")),
                vn.t3nexus.oauth2.domain.user_account.UserAccountId.of(PENDING_ID), null, "IDP-9",
                new vn.t3nexus.oauth2.domain.login_session.LoginContext("203.0.113.7", "Mozilla/5.0"),
                NOW.plus(Duration.ofDays(1)), NOW);
        fx.sessionRepository.save(forPending);

        Throwable thrown = catchThrowable(() -> gate().handle(new ResolveAuthorizationSession.Command(
                forPending.getId().getValueAsString(), "IDP-9", "shop-web")));

        assertThat(thrown).isInstanceOfSatisfying(UserAccountException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(UserAccountErrorCode.ACCOUNT_NOT_ACTIVE));
    }

    @Test
    void a_database_failure_while_reading_the_session_is_not_swallowed() {
        LoginSessionRepository failing = new LoginSessionRepositoryDelegate(fx.sessionRepository) {
            @Override public java.util.Optional<LoginSession> findById(LoginSessionId id) {
                throw new IllegalStateException("database down");
            }
        };

        Throwable thrown = catchThrowable(() -> gate(fx.clock, failing).handle(command("IDP-1", "shop-web")));

        assertThat(thrown).isInstanceOf(IllegalStateException.class).hasMessage("database down");
    }
}
