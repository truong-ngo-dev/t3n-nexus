package vn.t3nexus.oauth2.infrastructure.security.extension.authentication;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import vn.t3nexus.oauth2.application.login_session.LoginTestFixture;
import vn.t3nexus.oauth2.application.login_session.authenticate_with_password.AuthenticateWithPassword;
import vn.t3nexus.oauth2.domain.login_session.LoginFailedEvent;
import vn.t3nexus.oauth2.domain.user_account.UserAccountId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static vn.t3nexus.oauth2.application.login_session.LoginTestFixture.ADMIN_ID;
import static vn.t3nexus.oauth2.application.login_session.LoginTestFixture.USER_ID;

/** Bộ xác thực mật khẩu của form đăng nhập: KT-03, 04 (phần Spring), 25; không để lộ mã nội bộ vào bản ghi ủy quyền. */
class PasswordAuthenticationProviderTest {

    private LoginTestFixture fx;
    private PasswordAuthenticationProvider provider;

    @BeforeEach
    void setUp() {
        fx = new LoginTestFixture();
        provider = new PasswordAuthenticationProvider(new AuthenticateWithPassword(fx.accountRepository,
                fx.passwordHasher, fx.clock), fx.clock);
        fx.verifyCalls.set(0);
    }

    private static UsernamePasswordAuthenticationToken request(String email, String password) {
        UsernamePasswordAuthenticationToken token = UsernamePasswordAuthenticationToken.unauthenticated(email, password);
        token.setDetails(new LoginRequestDetails("203.0.113.7", "SESSION-BEFORE-LOGIN", "203.0.113.7",
                "Mozilla/5.0 Chrome/130", "h(raw-device-cookie)"));
        return token;
    }

    @Test
    void a_correct_password_gives_an_authenticated_token_for_the_account_with_its_role() {
        Authentication result = provider.authenticate(request("nguyen@example.com", "matkhau123"));

        assertThat(result.isAuthenticated()).isTrue();
        assertThat(result.getName()).isEqualTo(USER_ID.toString());
        assertThat(result.getAuthorities()).extracting(GrantedAuthority::getAuthority).containsExactly("ROLE_USER", "FACTOR_PASSWORD");
        assertThat(result.getAuthorities()).filteredOn(a -> a instanceof org.springframework.security.core.authority.FactorGrantedAuthority)
                .singleElement().satisfies(a -> assertThat(((org.springframework.security.core.authority.FactorGrantedAuthority) a).getIssuedAt())
                        .isEqualTo(vn.t3nexus.oauth2.application.login_session.LoginTestFixture.NOW));   // auth_time của ID token
        assertThat(provider.authenticate(request("admin@t3nexus.com.vn", "quantri123")).getAuthorities())
                .extracting(GrantedAuthority::getAuthority).containsExactly("ROLE_ADMIN", "FACTOR_PASSWORD");
        assertThat(provider.authenticate(request("admin@t3nexus.com.vn", "quantri123")).getName()).isEqualTo(ADMIN_ID.toString());
    }

    @Test
    void the_token_is_stored_with_the_session_and_the_authorization_so_it_holds_no_secret() {
        // Qua ProviderManager như trong chuỗi lọc: nó chép details của yêu cầu sang kết quả vì bộ xác thực không đặt details
        Authentication result = new ProviderManager(provider).authenticate(request("nguyen@example.com", "matkhau123"));

        assertThat(result.getDetails()).isInstanceOfSatisfying(LoginRequestDetails.class, details -> {
            assertThat(details.getDeviceKeyHash()).isEqualTo("h(raw-device-cookie)");     // chỉ bản băm, không có mã thô
            assertThat(details.getIpAddress()).isEqualTo("203.0.113.7");
        });
        assertThat(result.getCredentials()).isNull();
        UserAccountDetails principal = (UserAccountDetails) result.getPrincipal();
        assertThat(principal.getUserId()).isEqualTo(USER_ID.toString());
        assertThat(principal.getEmail()).isEmpty();
        assertThat(principal.getPassword()).isNullOrEmpty();   // ProviderManager còn xóa thông tin đăng nhập của chủ thể
        assertThat(principal.isMfaEnabled()).isFalse();
        assertThat(result.toString()).doesNotContain("raw-device-cookie").doesNotContain("hashed:");
    }

    @Test
    void wrong_password_and_unknown_email_fail_the_same_way() {
        Throwable wrongPassword = catchThrowable(() -> provider.authenticate(request("nguyen@example.com", "sai-mat-khau-1")));
        Throwable unknownEmail  = catchThrowable(() -> provider.authenticate(request("khong-co@example.com", "matkhau123")));

        assertThat(wrongPassword).isInstanceOf(BadCredentialsException.class);
        assertThat(unknownEmail).isInstanceOf(BadCredentialsException.class);
        assertThat(unknownEmail.getMessage()).isEqualTo(wrongPassword.getMessage());
    }

    @Test
    void a_failed_attempt_costs_exactly_one_password_verification() {
        catchThrowable(() -> provider.authenticate(request("nguyen@example.com", "sai-mat-khau-1")));
        int forWrongPassword = fx.verifyCalls.getAndSet(0);
        catchThrowable(() -> provider.authenticate(request("khong-co@example.com", "matkhau123")));

        assertThat(forWrongPassword).isEqualTo(1);               // không có bộ xác thực thứ hai băm lần nữa
        assertThat(fx.verifyCalls.get()).isEqualTo(1);
    }

    @Test
    void an_account_that_is_not_active_is_reported_as_locked_only_after_the_password_is_right() {
        Throwable correct = catchThrowable(() -> provider.authenticate(request("pending@example.com", "matkhau123")));
        Throwable wrong   = catchThrowable(() -> provider.authenticate(request("pending@example.com", "sai-mat-khau-1")));

        assertThat(correct).isInstanceOf(LockedException.class);
        assertThat(wrong).isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void the_request_details_reach_the_failure_event_hashed() {
        catchThrowable(() -> provider.authenticate(request("nguyen@example.com", "sai-mat-khau-1")));

        var events = fx.accounts.get(UserAccountId.of(USER_ID)).getDomainEvents();
        assertThat(events).singleElement().isInstanceOfSatisfying(LoginFailedEvent.class, e -> {
            String payload = e.getPayload().toString();
            assertThat(payload).contains("203.0.113.7", "Mozilla/5.0 Chrome/130", "h(raw-device-cookie)");
            assertThat(payload.replace("h(raw-device-cookie)", "")).doesNotContain("raw-device-cookie");
        });
    }

    @Test
    void a_request_without_details_still_authenticates() {
        Authentication result = provider.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated("nguyen@example.com", "matkhau123"));

        assertThat(result.isAuthenticated()).isTrue();
    }

    @Test
    void only_username_and_password_tokens_are_supported() {
        assertThat(provider.supports(UsernamePasswordAuthenticationToken.class)).isTrue();
        assertThat(provider.supports(TestingAuthenticationToken.class)).isFalse();
    }
}
