package vn.t3nexus.catalog.domain;

import vn.t3nexus.lib.utils.lang.StringUtils;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Tên hiển thị và khóa so trùng của một tên (danh mục, mẫu thuộc tính, tùy chọn, thương hiệu).
 * <br>Hiển thị: bỏ khoảng trắng đầu cuối, gộp khoảng trắng giữa thành một dấu cách.
 * <br>Khóa: từ hiển thị, bỏ dấu tiếng Việt ({@code đ} thành {@code d}), chữ thường. Hai tên cùng khóa là trùng nhau.
 */
public record NameKey(String display, String key) {

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    public static NameKey of(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Tên không được rỗng");
        }
        String display = WHITESPACE.matcher(raw.strip()).replaceAll(" ");
        String key = StringUtils.removeAccent(display).toLowerCase(Locale.ROOT);
        return new NameKey(display, key);
    }
}
