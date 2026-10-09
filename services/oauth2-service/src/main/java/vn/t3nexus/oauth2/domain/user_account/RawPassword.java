package vn.t3nexus.oauth2.domain.user_account;

import vn.t3nexus.lib.common.domain.model.ValueObject;

import java.util.regex.Pattern;

/**
 * Mật khẩu hợp lệ chưa băm: 8 đến 64 ký tự, chỉ gồm chữ {@code A-Za-z}, số {@code 0-9} và hai ký tự {@code @} {@code $};
 * có ít nhất một chữ cái và một chữ số. Dấu cách, emoji, chữ có dấu bị từ chối. Toàn ASCII nên 1 ký tự là 1 byte và luôn
 * dưới giới hạn 72 byte của bcrypt (ADR-0002). Không bao giờ in ra: {@link #toString()} che giá trị.
 */
public final class RawPassword implements ValueObject {

    public static final int MIN_LENGTH = 8;
    public static final int MAX_LENGTH = 64;

    private static final Pattern ALLOWED   = Pattern.compile("[A-Za-z0-9@$]+");
    private static final Pattern HAS_LETTER = Pattern.compile("[A-Za-z]");
    private static final Pattern HAS_DIGIT  = Pattern.compile("[0-9]");

    private final String value;

    private RawPassword(String value) {
        this.value = value;
    }

    public static RawPassword of(String raw) {
        if (raw == null
                || raw.length() < MIN_LENGTH || raw.length() > MAX_LENGTH
                || !ALLOWED.matcher(raw).matches()
                || !HAS_LETTER.matcher(raw).find()
                || !HAS_DIGIT.matcher(raw).find()) {
            throw UserAccountException.passwordInvalid();
        }
        return new RawPassword(raw);
    }

    public String value() {
        return value;
    }

    @Override
    public String toString() {
        return "RawPassword[****]";
    }
}
