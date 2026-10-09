package vn.t3nexus.oauth2.domain.user_account;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** KT-01 (phần miền), KT-10 */
class UserAccountTest {

    private static final Instant NOW = Instant.parse("2026-10-08T03:00:00Z");

    private final UserAccountId id = UserAccountId.of(UUID.fromString("01938a5e-1c00-7000-8000-000000000001"));
    private final Email email = Email.of("a@example.com");
    private final PasswordHash hash = PasswordHash.ofHashed("$2a$10$hash");

    @Test
    void register_creates_active_user_role_with_given_id_and_time() {
        UserAccount account = UserAccount.register(id, email, hash, NOW);

        assertThat(account.getId()).isEqualTo(id);
        assertThat(account.getEmail()).isEqualTo(email);
        assertThat(account.getRole()).isEqualTo(Role.USER);
        assertThat(account.getStatus()).isEqualTo(UserAccountStatus.ACTIVE);
        assertThat(account.getPasswordHash()).isEqualTo(hash);
        assertThat(account.getCreatedAt()).isEqualTo(NOW);
        assertThat(account.getUpdatedAt()).isEqualTo(NOW);
    }

    @Test
    void register_records_one_event_with_id_email_and_role_only() {
        UserAccount account = UserAccount.register(id, email, hash, NOW);

        assertThat(account.getDomainEvents()).hasSize(1);
        UserAccountRegistered event = (UserAccountRegistered) account.getDomainEvents().iterator().next();
        assertThat(event.getUserAccountId()).isEqualTo(id.getValueAsString());
        assertThat(event.getEmail()).isEqualTo("a@example.com");
        assertThat(event.getRole()).isEqualTo("USER");
        assertThat(event.getOccurredOn()).isEqualTo(NOW);
        assertThat(event.getAggregateId()).isEqualTo(id.getValueAsString());
        assertThat(event.getPayload().toString()).doesNotContain("$2a$").doesNotContain("password");
    }

    @Test
    void account_has_no_way_to_change_its_role() {
        assertThat(UserAccount.class.getMethods())
                .noneMatch(m -> m.getName().equalsIgnoreCase("setRole") || m.getName().equalsIgnoreCase("changeRole"));
    }

    @Test
    void verify_password_passes_own_hash_to_the_hasher() {
        UserAccount account = UserAccount.register(id, email, hash, NOW);
        PasswordHasher hasher = new PasswordHasher() {
            public PasswordHash hash(RawPassword raw) { throw new UnsupportedOperationException(); }
            public boolean verify(String attempt, PasswordHash stored) {
                return stored.equals(hash) && attempt.equals("right-pass-1");
            }
        };

        assertThat(account.verifyPassword("right-pass-1", hasher)).isTrue();
        assertThat(account.verifyPassword("wrong", hasher)).isFalse();
    }

    @Test
    void reconstitute_keeps_stored_values_and_records_no_event() {
        UserAccount account = UserAccount.reconstitute(id, email, hash, Role.ADMIN, RegistrationMethod.CREDENTIAL,
                UserAccountStatus.ACTIVE, false, NOW, NOW);

        assertThat(account.getRole()).isEqualTo(Role.ADMIN);
        assertThat(account.getDomainEvents()).isEmpty();
    }
}
