package vn.t3nexus.oauth2.application.login_session.record_client_session_issued;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.t3nexus.oauth2.application.login_session.LoginTestFixture;
import vn.t3nexus.oauth2.application.login_session.open_login_session.OpenLoginSession;
import vn.t3nexus.oauth2.domain.login_session.ClientSessionId;
import vn.t3nexus.oauth2.domain.login_session.ClientSessionIssuedEvent;
import vn.t3nexus.oauth2.domain.login_session.LoginSession;
import vn.t3nexus.oauth2.domain.login_session.LoginSessionErrorCode;
import vn.t3nexus.oauth2.domain.login_session.LoginSessionException;
import vn.t3nexus.oauth2.domain.login_session.LoginSessionId;
import vn.t3nexus.oauth2.domain.login_session.LoginSessionRepository;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static vn.t3nexus.oauth2.application.login_session.LoginTestFixture.NOW;
import static vn.t3nexus.oauth2.application.login_session.LoginTestFixture.USER_ID;

/** Điểm móc lúc token được cấp, trên cổng giả: KT-19, 22 (phần use case), đối chiếu `sid`, KT-26. */
class RecordClientSessionIssuedTest {

    private static final String SID_A = "01938a5e-1c00-7000-8000-0000000000a1";
    private static final String SID_B = "01938a5e-1c00-7000-8000-0000000000a2";

    private LoginTestFixture fx;
    private RecordClientSessionIssued record;
    private String loginSessionId;

    @BeforeEach
    void setUp() {
        fx = new LoginTestFixture();
        record = new RecordClientSessionIssued(fx.sessionRepository, fx.clock);
        loginSessionId = new OpenLoginSession(fx.accountRepository, fx.sessionRepository, fx.deviceKeyHasher,
                fx.lifetimePolicy, fx.ids, fx.clock)
                .handle(new OpenLoginSession.Command(USER_ID.toString(), "raw-device-cookie", "203.0.113.7",
                        "Mozilla/5.0", "IDP-1")).loginSessionId();
        fx.sessions.get(LoginSessionId.of(loginSessionId)).clearDomainEvents();
    }

    private RecordClientSessionIssued.Command command(String sid, String authorizationId, String tokenId) {
        return new RecordClientSessionIssued.Command(loginSessionId, "shop-web", sid, authorizationId, tokenId);
    }

    private LoginSession session() {
        return fx.sessions.get(LoginSessionId.of(loginSessionId));
    }

    @Test
    void the_first_token_creates_the_client_session_with_the_sid_chosen_at_authorize() {
        RecordClientSessionIssued.Result result = record.handle(command(SID_A, "auth-1", "jti-1"));

        assertThat(result.replacedAuthorizationId()).isEmpty();
        assertThat(session().clientSessions()).singleElement().satisfies(c -> {
            assertThat(c.getId()).isEqualTo(ClientSessionId.of(SID_A));
            assertThat(c.authorizationId()).isEqualTo("auth-1");
        });
    }

    @Test
    void the_event_id_comes_from_the_token_id_and_the_event_has_no_internal_identifiers() {
        // KT-25 (phần event)
        record.handle(command(SID_A, "auth-1", "jti-1"));

        assertThat(session().getDomainEvents()).singleElement().isInstanceOfSatisfying(ClientSessionIssuedEvent.class, e -> {
            assertThat(e.getEventId()).isEqualTo("jti-1");
            assertThat(e.getPayload().toString()).doesNotContain("auth-1").doesNotContain("IDP-1");
        });
    }

    @Test
    void a_refresh_of_the_same_authorization_changes_nothing_and_records_an_event() {
        record.handle(command(SID_A, "auth-1", "jti-1"));
        session().clearDomainEvents();

        RecordClientSessionIssued.Result result = record.handle(command(SID_A, "auth-1", "jti-2"));

        assertThat(result.replacedAuthorizationId()).isEmpty();
        assertThat(session().clientSessions()).hasSize(1);
        assertThat(session().clientSessions().get(0).authorizationId()).isEqualTo("auth-1");
        assertThat(session().getDomainEvents()).singleElement()
                .satisfies(e -> assertThat(e.getEventId()).isEqualTo("jti-2"));
    }

    @Test
    void silent_authentication_relinks_keeps_the_sid_and_reports_the_authorization_to_delete() {
        record.handle(command(SID_A, "auth-1", "jti-1"));

        // xác thực im lặng: cùng sid (dùng lại ở bước cấp mã) nhưng bản ghi ủy quyền mới
        RecordClientSessionIssued.Result result = record.handle(command(SID_A, "auth-2", "jti-2"));

        assertThat(result.replacedAuthorizationId()).contains("auth-1");
        assertThat(session().clientSessions()).singleElement().satisfies(c -> {
            assertThat(c.getId()).isEqualTo(ClientSessionId.of(SID_A));
            assertThat(c.authorizationId()).isEqualTo("auth-2");
        });
    }

    @Test
    void a_sid_that_does_not_match_the_existing_client_session_is_rejected_and_nothing_is_saved() {
        record.handle(command(SID_A, "auth-1", "jti-1"));
        // Kho giả trả cùng một đối tượng nên không thể đọc lại để biết "chưa lưu": đếm lệnh lưu để chứng minh.
        int[] saves = {0};
        LoginSessionRepository watching = new vn.t3nexus.oauth2.application.login_session.resolve_authorization_session
                .LoginSessionRepositoryDelegate(fx.sessionRepository) {
            @Override public void save(LoginSession s) { saves[0]++; super.save(s); }
        };

        // một yêu cầu khác đã tạo phiên ứng dụng SID_A; token này mang SID_B (đua nhau) và bản ghi ủy quyền khác
        Throwable thrown = catchThrowable(() -> new RecordClientSessionIssued(watching, fx.clock)
                .handle(command(SID_B, "auth-2", "jti-2")));

        assertThat(thrown).isInstanceOfSatisfying(LoginSessionException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(LoginSessionErrorCode.CLIENT_SESSION_MISMATCH));
        assertThat(saves[0]).isZero();
    }

    @Test
    void a_unique_constraint_violation_on_save_is_not_retried() {
        int[] saves = {0};
        LoginSessionRepository conflicting = new vn.t3nexus.oauth2.application.login_session.resolve_authorization_session
                .LoginSessionRepositoryDelegate(fx.sessionRepository) {
            @Override public void save(LoginSession s) {
                saves[0]++;
                throw LoginSessionException.clientSessionConflict();
            }
        };

        Throwable thrown = catchThrowable(() -> new RecordClientSessionIssued(conflicting, fx.clock)
                .handle(command(SID_A, "auth-1", "jti-1")));

        assertThat(thrown).isInstanceOfSatisfying(LoginSessionException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(LoginSessionErrorCode.CLIENT_SESSION_CONFLICT));
        assertThat(saves[0]).isEqualTo(1);
    }

    @Test
    void an_unknown_login_session_is_rejected() {
        fx.sessions.clear();

        Throwable thrown = catchThrowable(() -> record.handle(command(SID_A, "auth-1", "jti-1")));

        assertThat(thrown).isInstanceOfSatisfying(LoginSessionException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(LoginSessionErrorCode.SESSION_EXPIRED));
    }

    @Test
    void an_expired_login_session_gets_no_client_session() {
        Clock afterExpiry = Clock.fixed(NOW.plus(Duration.ofDays(31)), ZoneOffset.UTC);

        Throwable thrown = catchThrowable(() -> new RecordClientSessionIssued(fx.sessionRepository, afterExpiry)
                .handle(command(SID_A, "auth-1", "jti-1")));

        assertThat(thrown).isInstanceOfSatisfying(LoginSessionException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(LoginSessionErrorCode.SESSION_EXPIRED));
        assertThat(session().clientSessions()).isEmpty();
    }
}
