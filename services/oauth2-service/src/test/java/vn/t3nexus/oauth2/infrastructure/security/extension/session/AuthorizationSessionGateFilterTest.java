package vn.t3nexus.oauth2.infrastructure.security.extension.session;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import vn.t3nexus.oauth2.application.login_session.LoginTestFixture;
import vn.t3nexus.oauth2.application.login_session.open_login_session.OpenLoginSession;
import vn.t3nexus.oauth2.application.login_session.resolve_authorization_session.LoginSessionRepositoryDelegate;
import vn.t3nexus.oauth2.application.login_session.resolve_authorization_session.ResolveAuthorizationSession;
import vn.t3nexus.oauth2.domain.login_session.LoginSessionId;
import vn.t3nexus.oauth2.domain.login_session.LoginSessionRepository;
import vn.t3nexus.oauth2.infrastructure.security.session.LoginSessionAttributes;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static vn.t3nexus.oauth2.application.login_session.LoginTestFixture.NOW;
import static vn.t3nexus.oauth2.application.login_session.LoginTestFixture.PENDING_ID;
import static vn.t3nexus.oauth2.application.login_session.LoginTestFixture.USER_ID;

/**
 * Cửa kiểm hạn ở `/oauth2/authorize` trên cổng giả: KT-23 (cookie Spring còn sống mà phiên của ta đã mất thì không cấp mã mà
 * quay về đăng nhập), KT-24 (ghi lại mã session đổi), và việc chuyển kết quả cho lớp bọc qua thuộc tính của yêu cầu.
 */
class AuthorizationSessionGateFilterTest {

    private static final RegisteredClient CLIENT = RegisteredClient.withId("rc-1").clientId("web-gateway")
            .clientSecret("{noop}x").authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("http://localhost/cb").build();

    private LoginTestFixture fx;
    private String loginSessionId;

    @BeforeEach
    void setUp() {
        fx = new LoginTestFixture();
        loginSessionId = new OpenLoginSession(fx.accountRepository, fx.sessionRepository, fx.deviceKeyHasher,
                fx.lifetimePolicy, fx.ids, fx.clock)
                .handle(new OpenLoginSession.Command(USER_ID.toString(), "raw", "203.0.113.7", "Mozilla/5.0", "IDP-1"))
                .loginSessionId();
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private AuthorizationSessionGateFilter filter(LoginSessionRepository repository) {
        return new AuthorizationSessionGateFilter(
                new ResolveAuthorizationSession(repository, fx.accountRepository, fx.ids, fx.clock),
                new InMemoryRegisteredClientRepository(CLIENT));
    }

    private static MockHttpServletRequest authorize(MockHttpSession session, String clientId) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/oauth2/authorize");
        request.setServletPath("/oauth2/authorize");
        request.setSession(session);
        if (clientId != null) request.setParameter("client_id", clientId);
        return request;
    }

    private static MockHttpSession sessionWith(String loginSessionId, String springSessionId) {
        MockHttpSession session = new MockHttpSession(null, springSessionId);
        if (loginSessionId != null) session.setAttribute(LoginSessionAttributes.LOGIN_SESSION_ID, loginSessionId);
        return session;
    }

    private static void authenticate() {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(USER_ID.toString(), null, AuthorityUtils.NO_AUTHORITIES));
    }

    private MockFilterChain run(AuthorizationSessionGateFilter filter, MockHttpServletRequest request) throws Exception {
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, new MockHttpServletResponse(), chain);
        return chain;
    }

    // ───────────── phiên còn hiệu lực ─────────────

    @Test
    void a_live_session_passes_the_gate_and_leaves_the_result_for_the_authorization_service() throws Exception {
        authenticate();
        MockHttpSession session = sessionWith(loginSessionId, "IDP-1");
        MockHttpServletRequest request = authorize(session, "web-gateway");

        MockFilterChain chain = run(filter(fx.sessionRepository), request);

        assertThat(chain.getRequest()).isNotNull();                       // yêu cầu đi tiếp
        assertThat(session.isInvalid()).isFalse();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(request.getAttribute(RequestAttributeAuthorizationSessionSource.ATTRIBUTE))
                .isInstanceOfSatisfying(ResolveAuthorizationSession.Result.class, r -> {
                    assertThat(r.loginSessionId()).isEqualTo(loginSessionId);
                    assertThat(r.sid()).isNotBlank();
                });
    }

    @Test
    void the_gate_uses_the_registered_client_id_not_the_client_id_parameter() throws Exception {
        authenticate();
        MockHttpServletRequest request = authorize(sessionWith(loginSessionId, "IDP-1"), "web-gateway");

        run(filter(fx.sessionRepository), request);

        // lần đăng nhập thứ hai cho cùng client chọn lại đúng sid đã cấp: chứng minh khóa là mã đăng ký, khớp với lớp bọc
        ResolveAuthorizationSession.Result first = (ResolveAuthorizationSession.Result)
                request.getAttribute(RequestAttributeAuthorizationSessionSource.ATTRIBUTE);
        fx.sessions.get(LoginSessionId.of(loginSessionId)).issueClientSession(
                new vn.t3nexus.oauth2.domain.login_session.ClientId("rc-1"),
                vn.t3nexus.oauth2.domain.login_session.ClientSessionId.of(first.sid()), "auth-1", "jti-1", NOW);
        MockHttpServletRequest second = authorize(sessionWith(loginSessionId, "IDP-1"), "web-gateway");
        run(filter(fx.sessionRepository), second);

        assertThat(((ResolveAuthorizationSession.Result) second.getAttribute(RequestAttributeAuthorizationSessionSource.ATTRIBUTE)).sid())
                .isEqualTo(first.sid());
    }

    @Test
    void a_changed_spring_session_id_is_written_back() throws Exception {
        authenticate();

        run(filter(fx.sessionRepository), authorize(sessionWith(loginSessionId, "IDP-ROTATED"), "web-gateway"));

        assertThat(fx.sessions.get(LoginSessionId.of(loginSessionId)).idpSessionId()).isEqualTo("IDP-ROTATED");
    }

    // ───────────── phiên không còn hợp lệ: quay về đăng nhập ─────────────

    @Test
    void a_session_that_is_gone_drops_the_authentication_so_that_spring_asks_for_a_login_again() throws Exception {
        authenticate();
        MockHttpSession session = sessionWith(loginSessionId, "IDP-1");
        fx.sessions.clear();                                              // cookie Spring còn sống, phiên của ta đã mất

        MockFilterChain chain = run(filter(fx.sessionRepository), authorize(session, "web-gateway"));

        assertThat(chain.getRequest()).isNotNull();                       // vẫn đi tiếp để Spring chuyển về trang đăng nhập
        assertThat(session.isInvalid()).isTrue();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void an_expired_session_drops_the_authentication() throws Exception {
        authenticate();
        MockHttpSession session = sessionWith(loginSessionId, "IDP-1");
        var afterExpiry = java.time.Clock.fixed(NOW.plus(Duration.ofDays(31)), java.time.ZoneOffset.UTC);
        AuthorizationSessionGateFilter late = new AuthorizationSessionGateFilter(
                new ResolveAuthorizationSession(fx.sessionRepository, fx.accountRepository, fx.ids, afterExpiry),
                new InMemoryRegisteredClientRepository(CLIENT));

        run(late, authorize(session, "web-gateway"));

        assertThat(session.isInvalid()).isTrue();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void an_authenticated_user_without_our_login_session_is_sent_back_to_log_in() throws Exception {
        authenticate();
        MockHttpSession session = sessionWith(null, "IDP-1");             // ví dụ đăng nhập bằng đường không mở phiên của ta

        run(filter(fx.sessionRepository), authorize(session, "web-gateway"));

        assertThat(session.isInvalid()).isTrue();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void an_account_that_is_no_longer_active_is_sent_back_to_log_in() throws Exception {
        authenticate();
        vn.t3nexus.oauth2.domain.login_session.LoginSession forPending = vn.t3nexus.oauth2.domain.login_session.LoginSession.open(
                LoginSessionId.of(UUID.fromString("01938a5e-1c00-7000-8000-0000000000f1")),
                vn.t3nexus.oauth2.domain.user_account.UserAccountId.of(PENDING_ID), null, "IDP-9",
                new vn.t3nexus.oauth2.domain.login_session.LoginContext("203.0.113.7", "Mozilla/5.0"),
                NOW.plus(Duration.ofDays(1)), NOW);
        fx.sessionRepository.save(forPending);
        MockHttpSession session = sessionWith(forPending.getId().getValueAsString(), "IDP-9");

        run(filter(fx.sessionRepository), authorize(session, "web-gateway"));

        assertThat(session.isInvalid()).isTrue();
    }

    // ───────────── không đụng tới ─────────────

    @Test
    void an_unauthenticated_request_goes_through_untouched() throws Exception {
        MockHttpSession session = sessionWith(null, "IDP-1");
        MockHttpServletRequest request = authorize(session, "web-gateway");

        MockFilterChain chain = run(filter(fx.sessionRepository), request);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(session.isInvalid()).isFalse();
        assertThat(request.getAttribute(RequestAttributeAuthorizationSessionSource.ATTRIBUTE)).isNull();
    }

    @Test
    void an_anonymous_user_goes_through_untouched() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new AnonymousAuthenticationToken("key", "anonymous", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));
        MockHttpSession session = sessionWith(null, "IDP-1");

        run(filter(fx.sessionRepository), authorize(session, "web-gateway"));

        assertThat(session.isInvalid()).isFalse();
    }

    @Test
    void an_unknown_client_goes_through_for_spring_to_reject() throws Exception {
        authenticate();
        MockHttpSession session = sessionWith(loginSessionId, "IDP-1");

        MockFilterChain chain = run(filter(fx.sessionRepository), authorize(session, "no-such-client"));

        assertThat(chain.getRequest()).isNotNull();
        assertThat(session.isInvalid()).isFalse();
    }

    @Test
    void other_paths_are_ignored() throws Exception {
        authenticate();
        MockHttpSession session = sessionWith(null, "IDP-1");
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/oauth2/token");
        request.setServletPath("/oauth2/token");
        request.setSession(session);

        run(filter(fx.sessionRepository), request);

        assertThat(session.isInvalid()).isFalse();
    }

    @Test
    void a_database_failure_is_not_swallowed_so_no_code_is_issued() {
        authenticate();
        LoginSessionRepository failing = new LoginSessionRepositoryDelegate(fx.sessionRepository) {
            @Override public Optional<vn.t3nexus.oauth2.domain.login_session.LoginSession> findById(LoginSessionId id) {
                throw new IllegalStateException("database down");
            }
        };
        MockHttpSession session = sessionWith(loginSessionId, "IDP-1");

        assertThatThrownBy(() -> run(filter(failing), authorize(session, "web-gateway")))
                .isInstanceOf(IllegalStateException.class);
        assertThat(session.isInvalid()).isFalse();
    }
}
