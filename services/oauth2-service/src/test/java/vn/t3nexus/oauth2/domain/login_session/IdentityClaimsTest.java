package vn.t3nexus.oauth2.domain.login_session;

import org.junit.jupiter.api.Test;
import vn.t3nexus.oauth2.domain.user_account.Role;
import vn.t3nexus.oauth2.domain.user_account.UserAccountId;

import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** KT-02 (phần miền): hợp đồng danh tính chỉ mang mã tài khoản, vai, `sid`, hạn (ADR-0003 mục 4). */
class IdentityClaimsTest {

    private final UserAccountId accountId = UserAccountId.of(UUID.fromString("01938a5e-1c00-7000-8000-000000000001"));
    private final ClientSessionId sid = ClientSessionId.of(UUID.fromString("01938a5e-1c00-7000-8000-0000000000b1"));
    private final Instant expiresAt = Instant.parse("2026-10-13T03:15:00Z");

    @Test
    void claims_carry_exactly_account_role_sid_and_expiry() {
        assertThat(Arrays.stream(IdentityClaims.class.getRecordComponents()).map(RecordComponent::getName))
                .containsExactly("accountId", "role", "sid", "expiresAt");
    }

    @Test
    void claims_hold_the_given_values() {
        IdentityClaims claims = new IdentityClaims(accountId, Role.ADMIN, sid, expiresAt);

        assertThat(claims.accountId()).isEqualTo(accountId);
        assertThat(claims.role()).isEqualTo(Role.ADMIN);
        assertThat(claims.sid()).isEqualTo(sid);
        assertThat(claims.expiresAt()).isEqualTo(expiresAt);
    }

    @Test
    void every_component_is_required() {
        assertThatThrownBy(() -> new IdentityClaims(null, Role.USER, sid, expiresAt)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new IdentityClaims(accountId, null, sid, expiresAt)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new IdentityClaims(accountId, Role.USER, null, expiresAt)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new IdentityClaims(accountId, Role.USER, sid, null)).isInstanceOf(IllegalArgumentException.class);
    }
}
