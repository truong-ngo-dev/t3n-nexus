package vn.t3nexus.oauth2.domain.user_account;

import vn.t3nexus.lib.common.domain.model.ValueObject;
import vn.t3nexus.lib.utils.lang.Assert;

import java.util.Objects;

/** Giá trị mật khẩu đã băm. Định dạng tự mô tả do bộ băm quyết định (ADR-0002 chờ chốt). */
public final class PasswordHash implements ValueObject {

    private final String hashedValue;

    private PasswordHash(String hashedValue) {
        this.hashedValue = hashedValue;
    }

    public static PasswordHash ofHashed(String hashedValue) {
        Assert.hasText(hashedValue, "hashedValue is required");
        return new PasswordHash(hashedValue);
    }

    public String getHashedValue() {
        return hashedValue;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof PasswordHash other && hashedValue.equals(other.hashedValue);
    }

    @Override
    public int hashCode() {
        return Objects.hash(hashedValue);
    }

    @Override
    public String toString() {
        return "PasswordHash[****]";
    }
}
