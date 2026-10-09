package vn.t3nexus.lib.observability.logging;

import java.util.regex.Pattern;

/**
 * Bộ mẫu nhận diện bí mật còn sót trong chuỗi (lưới an toàn cuối): token mang tên {@code Bearer}, JWT, mã băm bcrypt.
 * Dùng chung cho {@code BodySanitizer} và bộ quét ở đầu ra log, để chỉ có một nơi định nghĩa.
 */
public final class SecretPatterns {

    public static final String MASK = "***";

    private static final Pattern BEARER = Pattern.compile("(?i)(bearer\\s+)[A-Za-z0-9._~+/=-]{8,}");
    private static final Pattern JWT = Pattern.compile("eyJ[A-Za-z0-9_-]{5,}\\.[A-Za-z0-9_-]{5,}\\.[A-Za-z0-9_-]*");
    private static final Pattern BCRYPT = Pattern.compile("\\$2[aby]\\$\\d{2}\\$[./A-Za-z0-9]{53}");

    private SecretPatterns() {
    }

    public static String scrub(String text) {
        if (text == null || text.isEmpty()) return text;
        String result = BEARER.matcher(text).replaceAll("$1" + MASK);
        result = JWT.matcher(result).replaceAll(MASK);
        return BCRYPT.matcher(result).replaceAll(MASK);
    }
}
