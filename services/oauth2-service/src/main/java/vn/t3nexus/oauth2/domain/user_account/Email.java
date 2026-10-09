package vn.t3nexus.oauth2.domain.user_account;

import vn.t3nexus.lib.common.domain.model.ValueObject;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Email đã chuẩn hóa: cắt khoảng trắng đầu cuối, chữ thường không phụ thuộc ngôn ngữ, tối đa 254 ký tự,
 * định dạng đơn giản (một {@code @}, phần miền có dấu chấm, không có khoảng trắng).
 */
public final class Email implements ValueObject {

    public static final int MAX_LENGTH = 254;

    private static final Pattern FORMAT = Pattern.compile("^[^@\\s]+@[^@\\s.]+(\\.[^@\\s.]+)+$");

    private final String value;

    private Email(String value) {
        this.value = value;
    }

    public static Email of(String raw) {
        if (raw == null) throw UserAccountException.emailInvalid();
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() > MAX_LENGTH || !FORMAT.matcher(normalized).matches()) {
            throw UserAccountException.emailInvalid();
        }
        return new Email(normalized);
    }

    public String value() {
        return value;
    }

    /** Phần miền, sau dấu {@code @}. */
    public String domain() {
        return value.substring(value.indexOf('@') + 1);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Email other && value.equals(other.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
