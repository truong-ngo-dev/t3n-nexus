package vn.t3nexus.oauth2.infrastructure.security.extension.session;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationCode;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.transaction.support.TransactionOperations;
import vn.t3nexus.oauth2.application.login_session.LoginTestFixture;
import vn.t3nexus.oauth2.application.login_session.open_login_session.OpenLoginSession;
import vn.t3nexus.oauth2.application.login_session.record_client_session_issued.RecordClientSessionIssued;
import vn.t3nexus.oauth2.domain.login_session.ClientId;
import vn.t3nexus.oauth2.domain.login_session.ClientSessionId;
import vn.t3nexus.oauth2.domain.login_session.LoginSession;
import vn.t3nexus.oauth2.domain.login_session.LoginSessionId;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static vn.t3nexus.oauth2.application.login_session.LoginTestFixture.NOW;
import static vn.t3nexus.oauth2.application.login_session.LoginTestFixture.USER_ID;

/**
 * Tra bản ghi ủy quyền theo `sid`, không quét theo giá trị token (ADR-0003 mục 8): refresh token theo tham số `sid`, access token
 * theo claim `sid`, mã ủy quyền chuyển cho lớp dưới, mọi kiểu khác không tìm thấy. KT-21 (phần lớp bọc).
 */
class SessionBoundAuthorizationServiceLookupTest {

    private static final String SID_A = "01938a5e-1c00-7000-8000-0000000000a1";
    private static final String SID_B = "01938a5e-1c00-7000-8000-0000000000b1";
    private static final String SID_C = "01938a5e-1c00-7000-8000-0000000000c1";   // phiên ứng dụng mà bản ghi không có ID token

    private static final RegisteredClient CLIENT = RegisteredClient.withId("rc-1").clientId("web-gateway")
            .clientSecret("{noop}x").authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("http://localhost/cb").build();

    /** Đếm số lần lớp dưới bị hỏi theo giá trị token, để chứng minh các kiểu không hỗ trợ không bị quét. */
    private static final class CountingService extends SingleRowAuthorizationService {
        int tokenLookups;
        @Override public OAuth2Authorization findByToken(String token, OAuth2TokenType tokenType) {
            tokenLookups++;
            return super.findByToken(token, tokenType);
        }
    }

    private LoginTestFixture fx;
    private CountingService delegate;
    private SessionBoundAuthorizationService service;
    private Optional<String> requestedSid = Optional.empty();
    private IdTokenHintVerifier verifier = hint -> Optional.empty();
    private String accessJwtA;

    @BeforeEach
    void setUp() {
        fx = new LoginTestFixture();
        delegate = new CountingService();
        String loginSessionId = new OpenLoginSession(fx.accountRepository, fx.sessionRepository, fx.deviceKeyHasher,
                fx.lifetimePolicy, fx.ids, fx.clock)
                .handle(new OpenLoginSession.Command(USER_ID.toString(), "raw", "203.0.113.7", "Mozilla/5.0", "IDP-1"))
                .loginSessionId();
        LoginSession session = fx.sessions.get(LoginSessionId.of(loginSessionId));
        session.issueClientSession(new ClientId("rc-1"), ClientSessionId.of(SID_A), "auth-1", "jti-1", NOW);
        session.issueClientSession(new ClientId("rc-2"), ClientSessionId.of(SID_B), "auth-2", "jti-2", NOW);
        session.issueClientSession(new ClientId("rc-3"), ClientSessionId.of(SID_C), "auth-3", "jti-3", NOW);

        accessJwtA = jwtWithSid(SID_A, "sig-a");
        delegate.save(authorization("auth-1", accessJwtA, "refresh-1"));
        delegate.save(authorization("auth-2", jwtWithSid(SID_B, "sig-b"), "refresh-2"));
        delegate.save(withoutIdToken("auth-3", jwtWithSid(SID_C, "sig-c"), "refresh-3"));

        service = new SessionBoundAuthorizationService(delegate,
                new RecordClientSessionIssued(fx.sessionRepository, fx.clock), Optional::empty,
                TransactionOperations.withoutTransaction(),
                new SidAuthorizationLookup(delegate, fx.sessionRepository, () -> requestedSid, hint -> verifier.verify(hint)));
        delegate.tokenLookups = 0;
    }

    private static String jwtWithSid(String sid, String signature) {
        Base64.Encoder url = Base64.getUrlEncoder().withoutPadding();
        String header  = url.encodeToString("{\"alg\":\"RS256\"}".getBytes(StandardCharsets.UTF_8));
        String payload = url.encodeToString(("{\"sub\":\"u\",\"sid\":\"" + sid + "\"}").getBytes(StandardCharsets.UTF_8));
        return header + "." + payload + "." + url.encodeToString(signature.getBytes(StandardCharsets.UTF_8));
    }

    private static OAuth2Authorization.Builder base(String id, String accessToken, String refreshToken) {
        return OAuth2Authorization.withRegisteredClient(CLIENT).id(id).principalName(USER_ID.toString())
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .token(new OAuth2AuthorizationCode("code-" + id, NOW, NOW.plusSeconds(300)))
                .token(new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, accessToken, NOW, NOW.plusSeconds(900)))
                .refreshToken(new OAuth2RefreshToken(refreshToken, NOW, Instant.parse("2026-10-21T03:00:00Z")));
    }

    /** Bản ghi có ID token mà `sid` là phiên ứng dụng của nó, như token do Xác thực cấp. */
    private static OAuth2Authorization authorization(String id, String accessToken, String refreshToken) {
        String sid = id.equals("auth-1") ? SID_A : SID_B;
        return base(id, accessToken, refreshToken)
                .token(new OidcIdToken(idTokenOf(id), NOW, NOW.plusSeconds(900), Map.of("sub", USER_ID.toString(), "sid", sid))).build();
    }

    private static OAuth2Authorization withoutIdToken(String id, String accessToken, String refreshToken) {
        return base(id, accessToken, refreshToken).build();
    }

    private static String idTokenOf(String id) {
        return jwtWithSid(id.equals("auth-1") ? SID_A : SID_B, "id-sig-" + id);
    }

    // ───────────── refresh token: theo tham số sid ─────────────

    @Test
    void a_refresh_token_is_found_by_the_sid_the_bff_sends() {
        requestedSid = Optional.of(SID_A);

        assertThat(service.findByToken("refresh-1", OAuth2TokenType.REFRESH_TOKEN)).extracting(OAuth2Authorization::getId)
                .isEqualTo("auth-1");
        assertThat(delegate.tokenLookups).isZero();               // không quét theo giá trị token
    }

    @Test
    void a_refresh_token_without_a_sid_is_not_found() {
        requestedSid = Optional.empty();

        assertThat(service.findByToken("refresh-1", OAuth2TokenType.REFRESH_TOKEN)).isNull();
        assertThat(delegate.tokenLookups).isZero();
    }

    @Test
    void a_refresh_token_presented_with_the_sid_of_another_client_session_is_not_found() {
        requestedSid = Optional.of(SID_B);

        assertThat(service.findByToken("refresh-1", OAuth2TokenType.REFRESH_TOKEN)).isNull();
        assertThat(service.findByToken("refresh-2", OAuth2TokenType.REFRESH_TOKEN)).isNotNull();
    }

    @Test
    void a_wrong_refresh_token_value_for_a_valid_sid_is_not_found() {
        requestedSid = Optional.of(SID_A);

        assertThat(service.findByToken("refresh-1-tampered", OAuth2TokenType.REFRESH_TOKEN)).isNull();
        assertThat(service.findByToken("", OAuth2TokenType.REFRESH_TOKEN)).isNull();
    }

    @Test
    void an_unknown_or_malformed_sid_is_not_found() {
        requestedSid = Optional.of("01938a5e-1c00-7000-8000-0000000fffff");
        assertThat(service.findByToken("refresh-1", OAuth2TokenType.REFRESH_TOKEN)).isNull();

        requestedSid = Optional.of("not-a-uuid");
        assertThat(service.findByToken("refresh-1", OAuth2TokenType.REFRESH_TOKEN)).isNull();
    }

    // ───────────── access token: theo claim sid ─────────────

    @Test
    void an_access_token_is_found_by_the_sid_claim_inside_it() {
        assertThat(service.findByToken(accessJwtA, OAuth2TokenType.ACCESS_TOKEN)).extracting(OAuth2Authorization::getId)
                .isEqualTo("auth-1");
        assertThat(delegate.tokenLookups).isZero();
    }

    @Test
    void a_forged_access_token_with_a_real_sid_but_another_value_is_not_found() {
        String forged = jwtWithSid(SID_A, "forged-signature");

        assertThat(service.findByToken(forged, OAuth2TokenType.ACCESS_TOKEN)).isNull();
    }

    @Test
    void an_access_token_that_is_not_a_jwt_or_has_no_sid_is_not_found() {
        String noSid = Base64.getUrlEncoder().withoutPadding().encodeToString("{\"alg\":\"RS256\"}".getBytes(StandardCharsets.UTF_8))
                + "." + Base64.getUrlEncoder().withoutPadding().encodeToString("{\"sub\":\"u\"}".getBytes(StandardCharsets.UTF_8))
                + ".c2ln";

        assertThat(service.findByToken("opaque-token-value", OAuth2TokenType.ACCESS_TOKEN)).isNull();
        assertThat(service.findByToken(noSid, OAuth2TokenType.ACCESS_TOKEN)).isNull();
        assertThat(delegate.tokenLookups).isZero();
    }

    // ───────────── mã ủy quyền và các kiểu khác ─────────────

    @Test
    void an_authorization_code_goes_to_the_underlying_service_which_has_an_index_for_it() {
        assertThat(service.findByToken("code-auth-1", new OAuth2TokenType("code"))).extracting(OAuth2Authorization::getId)
                .isEqualTo("auth-1");
        assertThat(delegate.tokenLookups).isEqualTo(1);
    }

    // ───────────── ID token của id_token_hint (feature 03 T1) ─────────────

    private static Jwt hint(String sid, String subject) {
        Jwt.Builder builder = Jwt.withTokenValue("hint").header("alg", "RS256").subject(subject);
        if (sid != null) builder.claim("sid", sid);
        return builder.build();
    }

    private static final OAuth2TokenType ID_TOKEN = new OAuth2TokenType("id_token");

    @Test
    void a_verified_hint_finds_the_authorization_by_its_sid_even_when_the_stored_id_token_has_changed() {
        // sau làm mới token, ID token đã lưu khác ID token mà bên gọi giữ từ lúc đăng nhập: chữ ký và sid đã gắn hint với đúng phiên
        verifier = token -> Optional.of(hint(SID_A, USER_ID.toString()));

        assertThat(service.findByToken("login-time-id-token", ID_TOKEN)).extracting(OAuth2Authorization::getId).isEqualTo("auth-1");
        assertThat(delegate.tokenLookups).isZero();
    }

    @Test
    void a_hint_that_fails_verification_is_not_found() {
        verifier = token -> Optional.empty();

        assertThat(service.findByToken("forged", ID_TOKEN)).isNull();
    }

    @Test
    void a_hint_with_no_sid_an_unknown_sid_or_another_subject_is_not_found() {
        verifier = token -> Optional.of(hint(null, USER_ID.toString()));
        assertThat(service.findByToken("h", ID_TOKEN)).isNull();

        verifier = token -> Optional.of(hint("01938a5e-1c00-7000-8000-0000000000ff", USER_ID.toString()));
        assertThat(service.findByToken("h", ID_TOKEN)).isNull();

        verifier = token -> Optional.of(hint(SID_A, "some-other-user"));
        assertThat(service.findByToken("h", ID_TOKEN)).isNull();
    }

    @Test
    void a_hint_for_an_authorization_without_an_id_token_is_not_found() {
        verifier = token -> Optional.of(hint(SID_C, USER_ID.toString()));

        assertThat(service.findByToken("h", ID_TOKEN)).isNull();
    }

    // ───────────── loại token không biết (introspect, revoke) ─────────────

    @Test
    void an_unknown_type_is_resolved_by_the_shape_of_the_token_without_scanning() {
        requestedSid = Optional.of(SID_A);

        assertThat(service.findByToken(accessJwtA, null)).extracting(OAuth2Authorization::getId).isEqualTo("auth-1");   // access token (JWT)
        assertThat(service.findByToken(idTokenOf("auth-1"), null)).extracting(OAuth2Authorization::getId).isEqualTo("auth-1");   // ID token (JWT)
        assertThat(service.findByToken("refresh-1", null)).extracting(OAuth2Authorization::getId).isEqualTo("auth-1");   // chuỗi mờ + sid của yêu cầu
        assertThat(delegate.tokenLookups).isZero();
    }

    @Test
    void an_unknown_type_with_a_wrong_value_or_no_sid_is_not_found() {
        assertThat(service.findByToken("refresh-1", null)).isNull();                         // chuỗi mờ, không có sid
        assertThat(service.findByToken(jwtWithSid(SID_A, "forged"), null)).isNull();          // sid thật, giá trị khác
        assertThat(service.findByToken("code-auth-1", null)).isNull();
        assertThat(delegate.tokenLookups).isZero();
    }

    @Test
    void the_types_this_service_does_not_use_are_not_found_and_never_scan() {
        assertThat(service.findByToken("x", new OAuth2TokenType("state"))).isNull();
        assertThat(service.findByToken("x", new OAuth2TokenType("device_code"))).isNull();
        assertThat(service.findByToken("x", new OAuth2TokenType("user_code"))).isNull();
        assertThat(service.findByToken(null, OAuth2TokenType.REFRESH_TOKEN)).isNull();
        assertThat(service.findByToken("", ID_TOKEN)).isNull();
        assertThat(delegate.tokenLookups).isZero();
    }
}
