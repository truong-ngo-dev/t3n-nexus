package vn.t3nexus.oauth2.infrastructure.security.extension.authentication;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import vn.t3nexus.oauth2.application.login_session.LoginTestFixture;
import vn.t3nexus.oauth2.application.login_session.open_login_session.OpenLoginSession;
import vn.t3nexus.oauth2.domain.login_session.DeviceKeyHash;
import vn.t3nexus.oauth2.domain.login_session.LoginSession;
import vn.t3nexus.oauth2.domain.login_session.LoginSessionId;
import vn.t3nexus.oauth2.domain.login_session.LoginSessionRepository;
import vn.t3nexus.oauth2.application.login_session.resolve_authorization_session.LoginSessionRepositoryDelegate;
import vn.t3nexus.oauth2.infrastructure.security.session.DeviceKeyCookie;
import vn.t3nexus.oauth2.infrastructure.security.session.DeviceKeyCookieIssuer;
import vn.t3nexus.oauth2.infrastructure.security.session.DeviceKeyCookieProperties;

import java.time.Duration;
import vn.t3nexus.oauth2.infrastructure.security.session.LoginSessionAttributes;


import static org.assertj.core.api.Assertions.assertThat;
import static vn.t3nexus.oauth2.application.login_session.LoginTestFixture.PENDING_ID;
import static vn.t3nexus.oauth2.application.login_session.LoginTestFixture.USER_ID;

/** Bộ xử lý thành công (mở phiên ở điểm cuối chuỗi xác thực, ADR-0003 mục 6) và bộ xử lý thất bại: KT-01, 24, 25 (phần Spring). */
class LoginHandlersTest {

    private LoginTestFixture fx;

    @BeforeEach
    void setUp() {
        fx = new LoginTestFixture();
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private LoginSessionAuthenticationSuccessHandler handler(LoginSessionRepository repository) {
        return new LoginSessionAuthenticationSuccessHandler(new OpenLoginSession(fx.accountRepository, repository,
                fx.deviceKeyHasher, fx.lifetimePolicy, fx.ids, fx.clock),
                new DeviceKeyCookieIssuer(() -> "generated-key", new DeviceKeyCookieProperties(Duration.ofDays(365), true)), fx.clock);
    }

    private static MockHttpServletRequest request(MockHttpSession session, boolean withDeviceCookie) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/login");
        request.setSession(session);
        request.setRemoteAddr("203.0.113.7");
        request.addHeader("User-Agent", "Mozilla/5.0 Chrome/130");
        if (withDeviceCookie) request.setCookies(new Cookie(DeviceKeyCookie.NAME, "raw-device-cookie"));
        return request;
    }

    private static Authentication authenticated(java.util.UUID accountId) {
        return UsernamePasswordAuthenticationToken.authenticated(accountId.toString(), null, java.util.List.of());
    }

    // ───────────── thành công ─────────────

    @Test
    void success_opens_a_login_session_with_the_final_spring_session_id_and_remembers_our_id() throws Exception {
        MockHttpSession session = new MockHttpSession(null, "SPRING-SESSION-FINAL");
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler(fx.sessionRepository).onAuthenticationSuccess(request(session, true), response, authenticated(USER_ID));

        assertThat(fx.sessions).hasSize(1);
        LoginSession opened = fx.sessions.values().iterator().next();
        assertThat(opened.idpSessionId()).isEqualTo("SPRING-SESSION-FINAL");
        assertThat(opened.deviceKey()).isEqualTo(new DeviceKeyHash("h(raw-device-cookie)"));
        assertThat(opened.userAccountId().getValueAsString()).isEqualTo(USER_ID.toString());
        assertThat(session.getAttribute(LoginSessionAttributes.LOGIN_SESSION_ID)).isEqualTo(opened.getId().getValueAsString());
        assertThat(response.getRedirectedUrl()).isEqualTo("/");
    }

    @Test
    void the_spring_session_lives_as_long_as_the_login_session_not_the_default_thirty_minutes() throws Exception {
        MockHttpSession session = new MockHttpSession(null, "SPRING-SESSION-FINAL");
        session.setMaxInactiveInterval(1800);                                   // mặc định của session vô danh trong Spring Session JDBC

        handler(fx.sessionRepository).onAuthenticationSuccess(request(session, true), new MockHttpServletResponse(), authenticated(USER_ID));

        LoginSession opened = fx.sessions.values().iterator().next();
        assertThat((long) session.getMaxInactiveInterval())
                .isEqualTo(Duration.between(fx.clock.instant(), opened.expiresAt()).getSeconds())
                .isGreaterThan(1800);
    }

    @Test
    void a_login_session_that_cannot_be_opened_leaves_no_session_to_extend() throws Exception {
        MockHttpSession session = new MockHttpSession(null, "SPRING-SESSION-FINAL");
        LoginSessionRepository failing = new LoginSessionRepositoryDelegate(fx.sessionRepository) {
            @Override public void save(LoginSession aggregate) { throw new IllegalStateException("database down"); }
        };

        handler(failing).onAuthenticationSuccess(request(session, true), new MockHttpServletResponse(), authenticated(USER_ID));

        assertThat(session.isInvalid()).isTrue();
    }

    @Test
    void success_without_a_device_cookie_still_opens_a_session() throws Exception {
        handler(fx.sessionRepository).onAuthenticationSuccess(request(new MockHttpSession(), false),
                new MockHttpServletResponse(), authenticated(USER_ID));

        assertThat(fx.sessions.values().iterator().next().deviceKey()).isNull();
    }

    @Test
    void the_session_attribute_holds_our_id_not_the_spring_session_id() throws Exception {
        MockHttpSession session = new MockHttpSession(null, "SPRING-SESSION-FINAL");

        handler(fx.sessionRepository).onAuthenticationSuccess(request(session, true), new MockHttpServletResponse(),
                authenticated(USER_ID));

        Object stored = session.getAttribute(LoginSessionAttributes.LOGIN_SESSION_ID);
        assertThat(stored).isNotEqualTo("SPRING-SESSION-FINAL");
        assertThat(fx.sessions.get(LoginSessionId.of((String) stored))).isNotNull();
    }

    // ───────────── không mở được phiên ─────────────

    @Test
    void when_the_session_cannot_be_opened_the_authentication_and_the_http_session_are_dropped() throws Exception {
        MockHttpSession session = new MockHttpSession();
        SecurityContextHolder.getContext().setAuthentication(authenticated(PENDING_ID));
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler(fx.sessionRepository).onAuthenticationSuccess(request(session, true), response, authenticated(PENDING_ID));

        assertThat(response.getRedirectedUrl()).isEqualTo("/login?error");
        assertThat(session.isInvalid()).isTrue();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(fx.sessions).isEmpty();
    }

    @Test
    void a_database_failure_while_opening_drops_the_authentication_too() throws Exception {
        LoginSessionRepository failing = new LoginSessionRepositoryDelegate(fx.sessionRepository) {
            @Override public void save(LoginSession aggregate) { throw new IllegalStateException("database down"); }
        };
        MockHttpSession session = new MockHttpSession();
        SecurityContextHolder.getContext().setAuthentication(authenticated(USER_ID));
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler(failing).onAuthenticationSuccess(request(session, true), response, authenticated(USER_ID));

        assertThat(response.getRedirectedUrl()).isEqualTo("/login?error");
        assertThat(session.isInvalid()).isTrue();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    // ───────────── thất bại ─────────────

    @Test
    void failures_go_back_to_the_login_page_with_the_right_message() throws Exception {
        PasswordLoginFailureHandler failure = new PasswordLoginFailureHandler();

        assertThat(redirect(failure, new BadCredentialsException("x"), null)).isEqualTo("/login?error");
        assertThat(redirect(failure, new LockedException("x"), null)).isEqualTo("/login?locked");
        assertThat(redirect(failure, new InternalAuthenticationServiceException("x"), null)).isEqualTo("/login?unavailable");
    }

    @Test
    void the_email_is_never_put_in_the_redirect_address() throws Exception {
        PasswordLoginFailureHandler failure = new PasswordLoginFailureHandler();

        for (var exception : java.util.List.of(new BadCredentialsException("x"), new LockedException("x"),
                new InternalAuthenticationServiceException("x"))) {
            assertThat(redirect(failure, exception, "nguyen@example.com")).doesNotContain("nguyen").doesNotContain("email");
        }
    }

    private static String redirect(PasswordLoginFailureHandler handler,
                                   org.springframework.security.core.AuthenticationException exception, String email) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/login");
        if (email != null) request.setParameter("username", email);
        MockHttpServletResponse response = new MockHttpServletResponse();
        handler.onAuthenticationFailure(request, response, exception);
        return response.getRedirectedUrl();
    }
}
