package vn.t3nexus.lib.observability.logging;

import java.util.regex.Pattern;

/**
 * Bộ mẫu nhận diện bí mật còn sót trong chuỗi (lưới an toàn cuối): token mang tên {@code Bearer}, JWT, mã băm bcrypt, tiêu đề
 * {@code Cookie}/{@code Set-Cookie} và giá trị các cookie phiên, mã thiết bị, CSRF. Dùng chung cho {@code BodySanitizer} và bộ quét ở
 * đầu ra log, để chỉ có một nơi định nghĩa.
 */
public final class SecretPatterns {

    public static final String MASK = "***";

    private static final Pattern BEARER = Pattern.compile("(?i)(bearer\\s+)[A-Za-z0-9._~+/=-]{8,}");
    private static final Pattern JWT = Pattern.compile("eyJ[A-Za-z0-9_-]{5,}\\.[A-Za-z0-9_-]{5,}\\.[A-Za-z0-9_-]*");
    private static final Pattern BCRYPT = Pattern.compile("\\$2[aby]\\$\\d{2}\\$[./A-Za-z0-9]{53}");
    /** Cả dòng tiêu đề {@code Cookie: ...} hoặc {@code Set-Cookie: ...}: che toàn bộ phần giá trị. */
    private static final Pattern COOKIE_HEADER = Pattern.compile("(?i)(\\b(?:set-)?cookie\\s*[:=]\\s*)[^\\r\\n\"]+");
    /** Cookie có tên đã biết xuất hiện lẻ (trong thông báo lỗi, chuỗi ghép): che giá trị, giữ tên để còn biết cookie nào. */
    private static final Pattern NAMED_COOKIE = Pattern.compile(
            "(?i)\\b(OAUTH2_SESSION|SESSION|JSESSIONID|DEVICE_KEY|XSRF-TOKEN)(=)[^;\\s,\"]+");

    private SecretPatterns() {
    }

    public static String scrub(String text) {
        if (text == null || text.isEmpty()) return text;
        String result = BEARER.matcher(text).replaceAll("$1" + MASK);
        result = JWT.matcher(result).replaceAll(MASK);
        result = COOKIE_HEADER.matcher(result).replaceAll("$1" + MASK);
        result = NAMED_COOKIE.matcher(result).replaceAll("$1$2" + MASK);
        return BCRYPT.matcher(result).replaceAll(MASK);
    }
}
