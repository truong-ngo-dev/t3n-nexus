package vn.t3nexus.oauth2.domain.login_session;

import vn.t3nexus.lib.common.domain.model.AbstractId;
import vn.t3nexus.lib.common.domain.model.Id;

import java.util.UUID;

/** Mã phiên người dùng: UUID v7 (ADR-0001), kiểu riêng để không nhầm với mã thực thể khác. */
public final class LoginSessionId extends AbstractId<UUID> implements Id<UUID> {

    private LoginSessionId(UUID value) {
        super(value);
    }

    public static LoginSessionId of(UUID value) {
        if (value == null) throw new IllegalArgumentException("LoginSessionId value is required");
        return new LoginSessionId(value);
    }

    public static LoginSessionId of(String value) {
        return of(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
