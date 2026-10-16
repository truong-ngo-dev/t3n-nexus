package vn.t3nexus.oauth2.domain.login_session;

import org.junit.jupiter.api.Test;
import vn.t3nexus.oauth2.domain.user_account.UserAccountId;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** KT-28 (phần miền): `LoginFailedEvent` mang bản băm mã thiết bị, không mang mã thô, mật khẩu hay email. */
class LoginFailedEventTest {

    private static final Instant NOW = Instant.parse("2026-10-13T03:00:00Z");

    private final UserAccountId accountId = UserAccountId.of(UUID.fromString("01938a5e-1c00-7000-8000-000000000001"));

    @Test
    void payload_has_the_hashed_device_key_context_and_reason() {
        LoginFailedEvent event = new LoginFailedEvent(accountId, "WRONG_PASSWORD", new DeviceKeyHash("hash-of-device-key"),
                new LoginContext("203.0.113.7", "Mozilla/5.0 Chrome/130"), NOW);

        assertThat(event.getOccurredOn()).isEqualTo(NOW);
        assertThat(event.getPayload().toString())
                .contains(accountId.getValueAsString(), "WRONG_PASSWORD", "hash-of-device-key",
                        "203.0.113.7", "Mozilla/5.0 Chrome/130");
    }

    @Test
    void payload_has_no_email_or_password_field() {
        LoginFailedEvent event = new LoginFailedEvent(accountId, "WRONG_PASSWORD", new DeviceKeyHash("hash-of-device-key"),
                new LoginContext("203.0.113.7", "Mozilla/5.0"), NOW);

        assertThat(event.getPayload().toString().toLowerCase()).doesNotContain("password=").doesNotContain("email");
    }

    @Test
    void payload_tolerates_a_missing_device_key_and_context() {
        LoginFailedEvent event = new LoginFailedEvent(accountId, "WRONG_PASSWORD", null, null, NOW);

        assertThat(event.getAggregateId()).isEqualTo(accountId.getValueAsString());
        assertThat(event.getPayload().toString()).contains(accountId.getValueAsString());
    }
}
