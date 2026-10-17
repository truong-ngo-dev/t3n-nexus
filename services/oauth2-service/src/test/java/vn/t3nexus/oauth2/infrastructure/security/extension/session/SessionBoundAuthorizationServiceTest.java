package vn.t3nexus.oauth2.infrastructure.security.extension.session;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationCode;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.transaction.support.TransactionOperations;
import vn.t3nexus.oauth2.application.login_session.LoginTestFixture;
import vn.t3nexus.oauth2.application.login_session.open_login_session.OpenLoginSession;
import vn.t3nexus.oauth2.application.login_session.record_client_session_issued.RecordClientSessionIssued;
import vn.t3nexus.oauth2.application.login_session.resolve_authorization_session.ResolveAuthorizationSession;
import vn.t3nexus.oauth2.domain.login_session.LoginSession;
import vn.t3nexus.oauth2.domain.login_session.LoginSessionId;
import vn.t3nexus.oauth2.domain.user_account.Role;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static vn.t3nexus.oauth2.application.login_session.LoginTestFixture.NOW;
import static vn.t3nexus.oauth2.application.login_session.LoginTestFixture.USER_ID;
import vn.t3nexus.oauth2.infrastructure.security.oauth2.token.SessionBinding;

/** Điểm móc `save()` trên cổng giả: KT-19, 22 (phần lớp bọc), 23; ADR-0003 mục 3, 9. */
class SessionBoundAuthorizationServiceTest {

    private static final String SID_A = "01938a5e-1c00-7000-8000-0000000000a1";

    private static final RegisteredClient CLIENT = RegisteredClient.withId("rc-1").clientId("web-gateway")
            .clientSecret("{noop}x").authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("http://localhost/cb").build();

    private LoginTestFixture fx;
    private SingleRowAuthorizationService delegate;
    private SessionBoundAuthorizationService service;
    private String loginSessionId;
    private Optional<ResolveAuthorizationSession.Result> gate = Optional.empty();
    private int sessionSaves;
    private Optional<String> requestedSid = Optional.empty();

    @BeforeEach
    void setUp() {
        fx = new LoginTestFixture();
        delegate = new SingleRowAuthorizationService();
        loginSessionId = new OpenLoginSession(fx.accountRepository, fx.sessionRepository, fx.deviceKeyHasher,
                fx.lifetimePolicy, fx.ids, fx.clock)
                .handle(new OpenLoginSession.Command(USER_ID.toString(), "raw-device-cookie", "203.0.113.7",
                        "Mozilla/5.0", "IDP-1")).loginSessionId();
        fx.sessions.get(LoginSessionId.of(loginSessionId)).clearDomainEvents();
        gate = Optional.of(new ResolveAuthorizationSession.Result(loginSessionId, SID_A,
                NOW.plus(Duration.ofDays(30)), Role.USER, "h(raw-device-cookie)"));
        service = new SessionBoundAuthorizationService(delegate,
                new RecordClientSessionIssued(countingSaves(fx.sessionRepository), fx.clock), () -> gate,
                TransactionOperations.withoutTransaction(),
                new SidAuthorizationLookup(delegate, fx.sessionRepository, () -> requestedSid, hint -> Optional.empty()));
    }

    /** Kho phiên giả giữ cùng một đối tượng nên không đọc lại được "chưa lưu": đếm lệnh lưu để chứng minh. */
    private vn.t3nexus.oauth2.domain.login_session.LoginSessionRepository countingSaves(
            vn.t3nexus.oauth2.domain.login_session.LoginSessionRepository repository) {
        return new vn.t3nexus.oauth2.application.login_session.resolve_authorization_session.LoginSessionRepositoryDelegate(repository) {
            @Override public void save(LoginSession s) { sessionSaves++; super.save(s); }
        };
    }

    private LoginSession session() {
        return fx.sessions.get(LoginSessionId.of(loginSessionId));
    }

    // ───────────── dựng bản ghi ủy quyền ─────────────

    private static OAuth2Authorization.Builder base(String id) {
        return OAuth2Authorization.withRegisteredClient(CLIENT).id(id).principalName(USER_ID.toString())
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE);
    }

    private static OAuth2Authorization pendingRequest(String id) {
        return base(id).build();
    }

    private static OAuth2Authorization withCode(String id) {
        return base(id).token(new OAuth2AuthorizationCode("code-" + id, NOW, NOW.plusSeconds(300))).build();
    }

    private static OAuth2Authorization withAccessToken(OAuth2Authorization codeStage, String jti, boolean invalidated) {
        OAuth2Authorization.Builder builder = OAuth2Authorization.from(codeStage)
                .token(new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "access-" + jti, NOW, NOW.plusSeconds(900)),
                        metadata -> {
                            metadata.put(OAuth2Authorization.Token.CLAIMS_METADATA_NAME, Map.of("jti", jti));
                            if (invalidated) metadata.put(OAuth2Authorization.Token.INVALIDATED_METADATA_NAME, true);
                        });
        return builder.build();
    }

    private static OAuth2Authorization noSession(String id, String jti) {
        return OAuth2Authorization.withRegisteredClient(CLIENT).id(id).principalName("service-x")
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .token(new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "access-" + jti, NOW, NOW.plusSeconds(900)),
                        metadata -> metadata.put(OAuth2Authorization.Token.CLAIMS_METADATA_NAME, Map.of("jti", jti)))
                .build();
    }

    /** Mã được cấp và đã gắn phiên, như lúc Spring lưu rồi đổi mã lấy token. */
    private OAuth2Authorization codeIssued(String id) {
        service.save(withCode(id));
        return delegate.findById(id);
    }

    // ───────────── Điểm móc 1: mã ủy quyền vừa được cấp ─────────────

    @Test
    void a_pending_request_without_a_code_is_saved_untouched() {
        service.save(pendingRequest("auth-1"));

        assertThat(SessionBinding.from(delegate.findById("auth-1"))).isEmpty();
    }

    @Test
    void the_authorization_code_is_bound_to_the_login_session_and_the_sid_chosen_at_the_gate() {
        service.save(withCode("auth-1"));

        SessionBinding binding = SessionBinding.from(delegate.findById("auth-1")).orElseThrow();
        assertThat(binding.loginSessionId()).isEqualTo(loginSessionId);
        assertThat(binding.sid()).isEqualTo(SID_A);
        assertThat(binding.sessionExpiresAt()).isEqualTo(NOW.plus(Duration.ofDays(30)));
        assertThat(binding.role()).isEqualTo(Role.USER);
        assertThat(session().clientSessions()).isEmpty();            // chưa có token nên chưa tạo phiên ứng dụng
    }

    @Test
    void the_binding_holds_no_spring_session_id_and_no_authorization_id() {
        service.save(withCode("auth-1"));

        assertThat(delegate.findById("auth-1").getAttributes().toString())
                .doesNotContain("IDP-1").doesNotContain("auth-1\"");
        assertThat(delegate.findById("auth-1").getAttributes().keySet())
                .containsExactlyInAnyOrder("login_session_id", "client_session_id", "session_expires_at", "account_role");
    }

    @Test
    void a_code_without_a_gate_result_is_rejected_with_login_required_and_nothing_is_saved() {
        gate = Optional.empty();

        Throwable thrown = catchThrowable(() -> service.save(withCode("auth-1")));

        assertThat(thrown).isInstanceOfSatisfying(OAuth2AuthenticationException.class,
                e -> assertThat(e.getError().getErrorCode()).isEqualTo("login_required"));
        assertThat(delegate.findById("auth-1")).isNull();
    }

    @Test
    void a_code_that_is_already_bound_is_not_bound_again() {
        OAuth2Authorization bound = codeIssued("auth-1");
        gate = Optional.empty();           // lần lưu lại không cần cửa kiểm hạn nữa

        service.save(bound);

        assertThat(SessionBinding.from(delegate.findById("auth-1"))).isPresent();
    }

    // ───────────── Điểm móc 2: token vừa được cấp ─────────────

    @Test
    void the_first_token_creates_the_client_session_with_the_chosen_sid() {
        OAuth2Authorization bound = codeIssued("auth-1");

        service.save(withAccessToken(bound, "jti-1", false));

        assertThat(session().clientSessions()).singleElement().satisfies(c -> {
            assertThat(c.getId().getValueAsString()).isEqualTo(SID_A);
            assertThat(c.authorizationId()).isEqualTo("auth-1");
            assertThat(c.clientId().value()).isEqualTo("rc-1");
        });
        assertThat(session().getDomainEvents()).singleElement()
                .satisfies(e -> assertThat(e.getEventId()).isEqualTo("jti-1"));
        assertThat(delegate.findById("auth-1").getToken(OAuth2AccessToken.class)).isNotNull();
    }

    @Test
    void a_refresh_saves_again_and_only_refreshes_the_same_client_session() {
        OAuth2Authorization first = withAccessToken(codeIssued("auth-1"), "jti-1", false);
        service.save(first);
        session().clearDomainEvents();

        service.save(withAccessToken(first, "jti-2", false));       // Spring cập nhật cùng bản ghi tại chỗ

        assertThat(session().clientSessions()).hasSize(1);
        assertThat(session().clientSessions().get(0).authorizationId()).isEqualTo("auth-1");
        assertThat(session().getDomainEvents()).singleElement()
                .satisfies(e -> assertThat(e.getEventId()).isEqualTo("jti-2"));
    }

    @Test
    void silent_authentication_relinks_and_removes_the_old_authorization_record() {
        service.save(withAccessToken(codeIssued("auth-1"), "jti-1", false));

        // cùng sid (dùng lại lúc cấp mã) nhưng bản ghi ủy quyền mới
        OAuth2Authorization second = withAccessToken(codeIssued("auth-2"), "jti-2", false);
        service.save(second);

        assertThat(session().clientSessions()).singleElement()
                .satisfies(c -> assertThat(c.authorizationId()).isEqualTo("auth-2"));
        assertThat(delegate.findById("auth-1")).isNull();
        assertThat(delegate.findById("auth-2")).isNotNull();
    }

    @Test
    void an_invalidated_access_token_does_not_touch_the_session() {
        OAuth2Authorization bound = codeIssued("auth-1");

        service.save(withAccessToken(bound, "jti-1", true));

        assertThat(session().clientSessions()).isEmpty();
        assertThat(session().getDomainEvents()).isEmpty();
    }

    @Test
    void an_authorization_outside_a_user_session_passes_straight_through() {
        service.save(noSession("svc-1", "jti-9"));

        assertThat(delegate.findById("svc-1")).isNotNull();
        assertThat(session().clientSessions()).isEmpty();
        assertThat(session().getDomainEvents()).isEmpty();
    }

    @Test
    void a_session_that_is_gone_turns_the_token_into_invalid_grant() {
        OAuth2Authorization bound = codeIssued("auth-1");
        fx.sessions.clear();

        Throwable thrown = catchThrowable(() -> service.save(withAccessToken(bound, "jti-1", false)));

        assertThat(thrown).isInstanceOfSatisfying(OAuth2AuthenticationException.class,
                e -> assertThat(e.getError().getErrorCode()).isEqualTo(OAuth2ErrorCodes.INVALID_GRANT));
    }

    @Test
    void a_sid_that_does_not_match_the_client_session_turns_the_token_into_invalid_grant() {
        service.save(withAccessToken(codeIssued("auth-1"), "jti-1", false));
        // một yêu cầu khác đã tạo phiên ứng dụng với SID_A; mã mới này lại mang sid khác
        gate = Optional.of(new ResolveAuthorizationSession.Result(loginSessionId, "01938a5e-1c00-7000-8000-0000000000a9",
                NOW.plus(Duration.ofDays(30)), Role.USER, null));
        OAuth2Authorization other = codeIssued("auth-2");
        int savesBefore = sessionSaves;

        Throwable thrown = catchThrowable(() -> service.save(withAccessToken(other, "jti-2", false)));

        assertThat(thrown).isInstanceOfSatisfying(OAuth2AuthenticationException.class,
                e -> assertThat(e.getError().getErrorCode()).isEqualTo(OAuth2ErrorCodes.INVALID_GRANT));
        assertThat(sessionSaves).isEqualTo(savesBefore);          // không lưu phiên khi sid không khớp
    }

    // ───────────── bên quan sát việc cấp token ─────────────

    private java.util.List<String> notified;

    private void withListeners(IssuedTokenListener... listeners) {
        notified = new java.util.ArrayList<>();
        service = new SessionBoundAuthorizationService(delegate,
                new RecordClientSessionIssued(countingSaves(fx.sessionRepository), fx.clock), () -> gate,
                TransactionOperations.withoutTransaction(),
                new SidAuthorizationLookup(delegate, fx.sessionRepository, () -> requestedSid, hint -> Optional.empty()),
                java.util.List.of(listeners));
    }

    @Test
    void the_listener_is_told_once_after_a_token_is_issued_and_not_at_any_other_save() {
        withListeners(a -> notified.add(a.getId()));

        service.save(pendingRequest("auth-0"));
        OAuth2Authorization bound = codeIssued("auth-1");                       // mã vừa cấp: chưa có token
        service.save(noSession("auth-2", "jti-x"));                              // ngoài phiên người dùng
        service.save(withAccessToken(bound, "jti-0", true));                     // token đã vô hiệu
        assertThat(notified).isEmpty();

        service.save(withAccessToken(bound, "jti-1", false));

        assertThat(notified).containsExactly("auth-1");
    }

    @Test
    void the_listener_is_not_told_when_the_session_rejects_the_token() {
        withListeners(a -> notified.add(a.getId()));
        OAuth2Authorization bound = codeIssued("auth-1");
        fx.sessions.clear();

        catchThrowable(() -> service.save(withAccessToken(bound, "jti-1", false)));

        assertThat(notified).isEmpty();
    }

    @Test
    void a_failing_listener_neither_breaks_the_issue_nor_stops_the_others() {
        withListeners(a -> { throw new IllegalStateException("boom"); }, a -> notified.add(a.getId()));

        service.save(withAccessToken(codeIssued("auth-1"), "jti-1", false));

        assertThat(notified).containsExactly("auth-1");
        assertThat(session().clientSessions()).hasSize(1);
    }

    // ───────────── chuyển nguyên ─────────────

    @Test
    void lookup_and_removal_go_to_the_underlying_service() {
        OAuth2Authorization bound = withAccessToken(codeIssued("auth-1"), "jti-1", false);
        service.save(bound);

        assertThat(service.findById("auth-1")).isNotNull();
        assertThat(service.findByToken("code-auth-1", new OAuth2TokenType("code"))).isNotNull();   // mã ủy quyền: chuyển nguyên

        service.remove(bound);

        assertThat(service.findById("auth-1")).isNull();
    }
}
