package vn.t3nexus.oauth2.application.user_account.register_user;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.t3nexus.lib.common.domain.service.IdGenerator;
import vn.t3nexus.oauth2.domain.user_account.Email;
import vn.t3nexus.oauth2.domain.user_account.InternalEmailPolicy;
import vn.t3nexus.oauth2.domain.user_account.PasswordHash;
import vn.t3nexus.oauth2.domain.user_account.PasswordHasher;
import vn.t3nexus.oauth2.domain.user_account.RawPassword;
import vn.t3nexus.oauth2.domain.user_account.Role;
import vn.t3nexus.oauth2.domain.user_account.UserAccount;
import vn.t3nexus.oauth2.domain.user_account.UserAccountErrorCode;
import vn.t3nexus.oauth2.domain.user_account.UserAccountException;
import vn.t3nexus.oauth2.domain.user_account.UserAccountId;
import vn.t3nexus.oauth2.domain.user_account.UserAccountRepository;
import vn.t3nexus.oauth2.domain.user_account.UserAccountService;
import vn.t3nexus.oauth2.domain.user_account.UserAccountStatus;
import vn.t3nexus.oauth2.infrastructure.adapter.service.ConfiguredInternalEmailPolicy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Use case đăng ký trên cổng giả, không chạm cơ sở dữ liệu (plan bước 6). KT-01, 03, 04, 05, 06, 07. */
class RegisterUserTest {

    private static final long UUID_MSB = 0x01938a5e1c007000L;
    private static final UUID FIRST_ID = new UUID(UUID_MSB, 1);
    private static final Instant NOW   = Instant.parse("2026-10-08T03:00:00Z");

    private final Map<UserAccountId, UserAccount> store = new HashMap<>();
    private RegisterUser registerUser;

    @BeforeEach
    void setUp() {
        UserAccountRepository repository = new UserAccountRepository() {
            public Optional<UserAccount> findById(UserAccountId id) { return Optional.ofNullable(store.get(id)); }
            public Optional<UserAccount> findByEmail(String email) {
                return store.values().stream().filter(a -> a.getEmail().value().equals(email)).findFirst();
            }
            public boolean existsByEmail(Email email) {
                return store.values().stream().anyMatch(a -> a.getEmail().equals(email));
            }
            public void save(UserAccount account) { store.put(account.getId(), account); }
            public void delete(UserAccountId id) { store.remove(id); }
        };
        PasswordHasher hasher = new PasswordHasher() {
            public PasswordHash hash(RawPassword raw) { return PasswordHash.ofHashed("hashed:" + raw.value()); }
            public boolean verify(String attempt, PasswordHash hash) { return hash.getHashedValue().equals("hashed:" + attempt); }
        };
        InternalEmailPolicy policy = new ConfiguredInternalEmailPolicy(List.of("t3nexus.com.vn"));
        java.util.concurrent.atomic.AtomicLong seq = new java.util.concurrent.atomic.AtomicLong();
        IdGenerator ids = () -> new UUID(UUID_MSB, seq.incrementAndGet());
        registerUser = new RegisterUser(repository, new UserAccountService(repository, policy), hasher, ids,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void registers_one_active_user_account_with_hashed_password() {
        RegisterUser.Result result = registerUser.handle(new RegisterUser.Command("  Nguyen@Example.com ", "matkhau123"));

        assertThat(result.userAccountId()).isEqualTo(FIRST_ID.toString());
        assertThat(store).hasSize(1);
        UserAccount saved = store.values().iterator().next();
        assertThat(saved.getEmail().value()).isEqualTo("nguyen@example.com");
        assertThat(saved.getRole()).isEqualTo(Role.USER);
        assertThat(saved.getStatus()).isEqualTo(UserAccountStatus.ACTIVE);
        assertThat(saved.getPasswordHash().getHashedValue()).isEqualTo("hashed:matkhau123").isNotEqualTo("matkhau123");
        assertThat(saved.getCreatedAt()).isEqualTo(NOW);
    }

    @Test
    void rejects_an_email_that_is_already_used_even_in_a_different_case() {
        registerUser.handle(new RegisterUser.Command("a@example.com", "matkhau123"));

        assertThatThrownBy(() -> registerUser.handle(new RegisterUser.Command(" A@EXAMPLE.COM", "matkhau123")))
                .isInstanceOf(UserAccountException.class)
                .extracting(e -> ((UserAccountException) e).getErrorCode()).isEqualTo(UserAccountErrorCode.EMAIL_TAKEN);
        assertThat(store).hasSize(1);
    }

    @Test
    void rejects_internal_domain_email_including_upper_case() {
        assertRejected("Admin@T3Nexus.com.vn", "matkhau123", UserAccountErrorCode.EMAIL_RESERVED);
    }

    @Test
    void does_not_block_a_domain_that_only_looks_like_the_internal_one() {
        registerUser.handle(new RegisterUser.Command("a@not-t3nexus.com.vn", "matkhau123"));
        registerUser.handle(new RegisterUser.Command("b@t3nexus.com.vn.example.org", "matkhau123"));

        assertThat(store).hasSize(2);
    }

    @Test
    void rejects_invalid_email_and_invalid_password_without_saving() {
        assertRejected("not-an-email", "matkhau123", UserAccountErrorCode.EMAIL_INVALID);
        assertRejected("a@example.com", "short1", UserAccountErrorCode.PASSWORD_INVALID);
        assertRejected("a@example.com", "onlyletters", UserAccountErrorCode.PASSWORD_INVALID);
        assertThat(store).isEmpty();
    }

    @Test
    void role_and_full_name_are_not_inputs() {
        assertThat(RegisterUser.Command.class.getRecordComponents())
                .extracting(c -> c.getName()).doesNotContain("role", "fullName");
    }

    @Test
    void collects_the_registered_event_in_the_aggregate() {
        registerUser.handle(new RegisterUser.Command("a@example.com", "matkhau123"));

        assertThat(store.values().iterator().next().getDomainEvents()).hasSize(1);
    }

    private void assertRejected(String email, String password, UserAccountErrorCode expected) {
        assertThatThrownBy(() -> registerUser.handle(new RegisterUser.Command(email, password)))
                .isInstanceOf(UserAccountException.class)
                .extracting(e -> ((UserAccountException) e).getErrorCode()).isEqualTo(expected);
    }
}
