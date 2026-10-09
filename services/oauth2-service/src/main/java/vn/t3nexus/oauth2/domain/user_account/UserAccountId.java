package vn.t3nexus.oauth2.domain.user_account;

import vn.t3nexus.lib.common.domain.model.AbstractId;
import vn.t3nexus.lib.common.domain.model.Id;

import java.util.UUID;

/** Mã tài khoản: UUID v7 (ADR-0001), kiểu riêng để không nhầm với mã thực thể khác. */
public final class UserAccountId extends AbstractId<UUID> implements Id<UUID> {

    private UserAccountId(UUID value) {
        super(value);
    }

    public static UserAccountId of(UUID value) {
        if (value == null) throw new IllegalArgumentException("UserAccountId value is required");
        return new UserAccountId(value);
    }

    public static UserAccountId of(String value) {
        return of(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
