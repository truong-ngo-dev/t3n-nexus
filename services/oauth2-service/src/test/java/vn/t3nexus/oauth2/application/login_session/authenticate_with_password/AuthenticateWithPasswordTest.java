package vn.t3nexus.oauth2.application.login_session.authenticate_with_password;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.t3nexus.oauth2.application.login_session.LoginTestFixture;
import vn.t3nexus.oauth2.domain.login_session.LoginFailedEvent;
import vn.t3nexus.oauth2.domain.user_account.Role;
import vn.t3nexus.oauth2.domain.user_account.UserAccountErrorCode;
import vn.t3nexus.oauth2.domain.user_account.UserAccountException;
import vn.t3nexus.oauth2.domain.user_account.UserAccountId;

import vn.t3nexus.oauth2.domain.user_account.Email;
import vn.t3nexus.oauth2.domain.user_account.PasswordHash;
import vn.t3nexus.oauth2.domain.user_account.UserAccount;
import vn.t3nexus.oauth2.domain.user_account.UserAccountRepository;

import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static vn.t3nexus.oauth2.application.login_session.LoginTestFixture.ADMIN_ID;
import static vn.t3nexus.oauth2.application.login_session.LoginTestFixture.NOW;
import static vn.t3nexus.oauth2.application.login_session.LoginTestFixture.PENDING_ID;
import static vn.t3nexus.oauth2.application.login_session.LoginTestFixture.USER_ID;

/** Nửa xác thực của đăng nhập trên cổng giả (không mở phiên): KT-03, 04, 05, 06, 28 (phần use case). */
class AuthenticateWithPasswordTest {

    private LoginTestFixture fx;
    private AuthenticateWithPassword authenticate;

    @BeforeEach
    void setUp() {
        fx = new LoginTestFixture();
        authenticate = new AuthenticateWithPassword(fx.accountRepository, fx.passwordHasher, fx.clock);
        fx.verifyCalls.set(0);
    }

    private AuthenticateWithPassword.Command command(String email, String password) {
        return new AuthenticateWithPassword.Command(email, password, fx.deviceKeyHasher.hash("raw-device-cookie"), "203.0.113.7",
                "Mozilla/5.0 Chrome/130");
    }

    private UserAccountException rejected(AuthenticateWithPassword.Command command) {
        Throwable thrown = catchThrowable(() -> authenticate.handle(command));
        assertThat(thrown).isInstanceOf(UserAccountException.class);
        return (UserAccountException) thrown;
    }

    // ───────────── Băm lại khi hệ số cũ (KT-07) ─────────────

    private void useLegacyHash(UUID id, String password) {
        UserAccount a = fx.accounts.get(UserAccountId.of(id));
        fx.accounts.put(a.getId(), UserAccount.reconstitute(a.getId(), a.getEmail(),
                PasswordHash.ofHashed(LoginTestFixture.LEGACY_PREFIX + password), a.getRole(), a.getRegistrationMethod(),
                a.getStatus(), a.isMfaEnabled(), a.getCreatedAt(), a.getUpdatedAt()));
    }

    @Test
    void a_correct_password_over_a_lower_cost_hash_is_rehashed_and_saved() {
        useLegacyHash(USER_ID, "matkhau123");

        authenticate.handle(command("nguyen@example.com", "matkhau123"));

        assertThat(fx.accounts.get(UserAccountId.of(USER_ID)).getPasswordHash().getHashedValue()).isEqualTo("hashed:matkhau123");
        assertThat(authenticate.handle(command("nguyen@example.com", "matkhau123")).userAccountId()).isEqualTo(UserAccountId.of(USER_ID));
    }

    @Test
    void a_wrong_password_over_a_lower_cost_hash_changes_nothing() {
        useLegacyHash(USER_ID, "matkhau123");

        rejected(command("nguyen@example.com", "sai-mat-khau-1"));

        assertThat(fx.accounts.get(UserAccountId.of(USER_ID)).getPasswordHash().getHashedValue())
                .isEqualTo(LoginTestFixture.LEGACY_PREFIX + "matkhau123");
    }

    @Test
    void a_current_cost_hash_is_not_saved_again() {
        AtomicInteger saves = new AtomicInteger();
        UserAccountRepository counting = new UserAccountRepository() {
            public Optional<UserAccount> findById(UserAccountId id) { return fx.accountRepository.findById(id); }
            public Optional<UserAccount> findByEmail(String email) { return fx.accountRepository.findByEmail(email); }
            public boolean existsByEmail(Email email) { return fx.accountRepository.existsByEmail(email); }
            public void save(UserAccount account) { saves.incrementAndGet(); fx.accountRepository.save(account); }
            public void delete(UserAccountId id) { fx.accountRepository.delete(id); }
        };

        new AuthenticateWithPassword(counting, fx.passwordHasher, fx.clock)
                .handle(command("nguyen@example.com", "matkhau123"));

        assertThat(saves).hasValue(0);
    }

    @Test
    void a_failure_to_save_the_new_hash_does_not_fail_the_login() {
        useLegacyHash(USER_ID, "matkhau123");
        UserAccountRepository failing = new UserAccountRepository() {
            public Optional<UserAccount> findById(UserAccountId id) { return fx.accountRepository.findById(id); }
            public Optional<UserAccount> findByEmail(String email) { return fx.accountRepository.findByEmail(email); }
            public boolean existsByEmail(Email email) { return fx.accountRepository.existsByEmail(email); }
            public void save(UserAccount account) { throw new IllegalStateException("database down"); }
            public void delete(UserAccountId id) { fx.accountRepository.delete(id); }
        };

        AuthenticateWithPassword.Result result = new AuthenticateWithPassword(failing, fx.passwordHasher, fx.clock)
                .handle(command("nguyen@example.com", "matkhau123"));

        assertThat(result.userAccountId()).isEqualTo(UserAccountId.of(USER_ID));
    }

    // ───────────── Đúng (KT-05) ─────────────

    @Test
    void a_correct_password_returns_the_account_and_role_without_opening_a_session() {
        AuthenticateWithPassword.Result result = authenticate.handle(command("nguyen@example.com", "matkhau123"));

        assertThat(result.userAccountId()).isEqualTo(UserAccountId.of(USER_ID));
        assertThat(result.role()).isEqualTo(Role.USER);
        assertThat(fx.sessions).isEmpty();
    }

    @Test
    void the_role_of_an_internal_account_is_returned() {
        assertThat(authenticate.handle(command("admin@t3nexus.com.vn", "quantri123")).role()).isEqualTo(Role.ADMIN);
        assertThat(authenticate.handle(command("admin@t3nexus.com.vn", "quantri123")).userAccountId())
                .isEqualTo(UserAccountId.of(ADMIN_ID));
    }

    @Test
    void email_is_normalized_before_the_lookup() {
        AuthenticateWithPassword.Result result = authenticate.handle(command("  Nguyen@Example.COM ", "matkhau123"));

        assertThat(result.userAccountId()).isEqualTo(UserAccountId.of(USER_ID));
    }

    @Test
    void a_successful_login_records_no_failure_event_on_the_account() {
        authenticate.handle(command("nguyen@example.com", "matkhau123"));

        assertThat(fx.accounts.get(UserAccountId.of(USER_ID)).getDomainEvents()).isEmpty();
    }

    // ───────────── Sai (KT-03, 04, 06, 28) ─────────────

    @Test
    void wrong_password_and_unknown_email_give_the_same_error_and_message() {
        UserAccountException wrongPassword = rejected(command("nguyen@example.com", "sai-mat-khau-1"));
        UserAccountException unknownEmail  = rejected(command("khong-co@example.com", "matkhau123"));

        assertThat(wrongPassword.getErrorCode()).isEqualTo(UserAccountErrorCode.INVALID_CREDENTIALS);
        assertThat(unknownEmail.getErrorCode()).isEqualTo(UserAccountErrorCode.INVALID_CREDENTIALS);
        assertThat(unknownEmail.getMessage()).isEqualTo(wrongPassword.getMessage());
    }

    @Test
    void an_unknown_email_still_runs_one_password_verification() {
        rejected(command("khong-co@example.com", "matkhau123"));
        int forUnknown = fx.verifyCalls.getAndSet(0);
        rejected(command("nguyen@example.com", "sai-mat-khau-1"));
        int forKnown = fx.verifyCalls.get();

        assertThat(forUnknown).isEqualTo(1);
        assertThat(forKnown).isEqualTo(forUnknown);
    }

    @Test
    void a_malformed_email_is_treated_like_an_unknown_email() {
        UserAccountException error = rejected(command("not-an-email", "matkhau123"));

        assertThat(error.getErrorCode()).isEqualTo(UserAccountErrorCode.INVALID_CREDENTIALS);
        assertThat(fx.verifyCalls.get()).isEqualTo(1);
    }

    @Test
    void password_rules_are_not_applied_when_logging_in() {
        UserAccountException error = rejected(command("nguyen@example.com", "x"));

        assertThat(error.getErrorCode()).isEqualTo(UserAccountErrorCode.INVALID_CREDENTIALS);
    }

    @Test
    void a_failed_login_for_an_existing_account_records_one_event_with_the_hashed_device_key() {
        rejected(command("nguyen@example.com", "sai-mat-khau-1"));

        var events = new ArrayList<>(fx.accounts.get(UserAccountId.of(USER_ID)).getDomainEvents());
        assertThat(events).hasSize(1);
        LoginFailedEvent event = (LoginFailedEvent) events.get(0);
        String payload = event.getPayload().toString();
        assertThat(event.getAggregateId()).isEqualTo(USER_ID.toString());
        assertThat(event.getOccurredOn()).isEqualTo(NOW);
        assertThat(payload).contains("h(raw-device-cookie)", "203.0.113.7", "Mozilla/5.0 Chrome/130");
        assertThat(payload.replace("h(raw-device-cookie)", "")).doesNotContain("raw-device-cookie")
                .doesNotContain("sai-mat-khau-1").doesNotContain("nguyen@example.com");
    }

    @Test
    void a_failed_login_for_an_unknown_email_records_no_event_anywhere() {
        rejected(command("khong-co@example.com", "matkhau123"));

        assertThat(fx.accounts.values()).allSatisfy(a -> assertThat(a.getDomainEvents()).isEmpty());
    }

    @Test
    void a_correct_password_on_an_account_that_is_not_active_is_rejected_and_recorded() {
        UserAccountException error = rejected(command("pending@example.com", "matkhau123"));

        assertThat(error.getErrorCode()).isEqualTo(UserAccountErrorCode.ACCOUNT_NOT_ACTIVE);
        assertThat(fx.accounts.get(UserAccountId.of(PENDING_ID)).getDomainEvents()).hasSize(1);
    }

    @Test
    void a_wrong_password_on_an_account_that_is_not_active_looks_like_any_wrong_password() {
        UserAccountException error = rejected(command("pending@example.com", "sai-mat-khau-1"));

        assertThat(error.getErrorCode()).isEqualTo(UserAccountErrorCode.INVALID_CREDENTIALS);
    }

    @Test
    void a_missing_device_cookie_still_authenticates_and_records_a_failure_without_a_device_key() {
        AuthenticateWithPassword.Command noCookie = new AuthenticateWithPassword.Command(
                "nguyen@example.com", "sai-mat-khau-1", null, "203.0.113.7", "Mozilla/5.0");

        rejected(noCookie);

        assertThat(fx.accounts.get(UserAccountId.of(USER_ID)).getDomainEvents()).hasSize(1);
    }
}
