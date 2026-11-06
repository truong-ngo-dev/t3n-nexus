package vn.t3nexus.lib.audit;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuditActorTargetTest {

    @Test
    void validActorAndTarget() {
        UUID uid = UUID.randomUUID();
        AuditActor userActor = new AuditActor(uid, "SELLER");
        assertThat(userActor.accountId()).isEqualTo(uid);
        assertThat(userActor.role()).isEqualTo("SELLER");

        AuditActor systemActor = new AuditActor(null, "SYSTEM");
        assertThat(systemActor.accountId()).isNull();
        assertThat(systemActor.role()).isEqualTo("SYSTEM");

        AuditTarget target = new AuditTarget("SELLER_PROFILE", "acc-123");
        assertThat(target.type()).isEqualTo("SELLER_PROFILE");
        assertThat(target.id()).isEqualTo("acc-123");
    }

    @Test
    void lowercaseRoleIsRejected() {
        assertThatThrownBy(() -> new AuditActor(UUID.randomUUID(), "seller"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Người làm không hợp lệ: role");
    }

    @Test
    void nullAccountIdForNonSystemRoleIsRejected() {
        assertThatThrownBy(() -> new AuditActor(null, "USER"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Người làm không hợp lệ: accountId");
    }

    @Test
    void nullAccountIdForSystemRoleIsAllowed() {
        AuditActor actor = new AuditActor(null, "SYSTEM");
        assertThat(actor.accountId()).isNull();
        assertThat(actor.role()).isEqualTo("SYSTEM");
    }

    @Test
    void invalidTargetTypePatternIsRejected() {
        assertThatThrownBy(() -> new AuditTarget("seller-profile", "123"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Đối tượng không hợp lệ: type");
    }

    @Test
    void blankTargetIdIsRejected() {
        assertThatThrownBy(() -> new AuditTarget("SELLER_PROFILE", "   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Đối tượng không hợp lệ: id");
    }

    @Test
    void targetIdExceeding100CharsIsRejected() {
        String longId = "a".repeat(101);
        assertThatThrownBy(() -> new AuditTarget("SELLER_PROFILE", longId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Đối tượng không hợp lệ: id");
    }
}
