package vn.t3nexus.lib.events;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Tên trường không được xuất hiện trong nội dung event. So khớp theo tên đầy đủ, không phân biệt hoa thường và dấu
 * phân cách ({@code access_token}, {@code Access-Token} và {@code accessToken} là một), không theo chuỗi con
 * ({@code tokenCount} hợp lệ). Chỉ kiểm tên, không kiểm giá trị.
 * <ul>
 *   <li>{@link #secretsOnly()}: bí mật (danh sách của {@code logging.md}). Mọi BC đều bị cấm.</li>
 *   <li>{@link #strict()}: bí mật và dữ liệu định danh (số căn cước, mã số thuế, địa chỉ, số điện thoại, email).
 *       Mặc định; BC có event cần mang email (ví dụ thông báo) chọn {@code secretsOnly()} một cách tường minh.</li>
 * </ul>
 */
public final class SensitiveFieldPolicy {

    private static final Set<String> SECRETS = Set.of(
            "password", "passwordhash", "token", "accesstoken", "refreshtoken", "idtoken", "otp", "secret",
            "clientsecret", "authorization", "cardnumber", "apikey");

    private static final Set<String> PERSONAL_IDENTIFIERS = Set.of(
            "identitynumber", "nationalid", "citizenid", "idcard", "taxcode", "taxid",
            "address", "phone", "phonenumber", "email");

    private final Set<String> forbidden;

    private SensitiveFieldPolicy(Set<String> forbidden) {
        this.forbidden = forbidden;
    }

    /** Không kiểm gì. Chỉ cho BC có event buộc mang bí mật; mỗi nơi dùng phải nêu lý do ở cấu hình. */
    public static SensitiveFieldPolicy none() {
        return new SensitiveFieldPolicy(Set.of());
    }

    public static SensitiveFieldPolicy secretsOnly() {
        return new SensitiveFieldPolicy(SECRETS);
    }

    public static SensitiveFieldPolicy strict() {
        Set<String> all = new HashSet<>(SECRETS);
        all.addAll(PERSONAL_IDENTIFIERS);
        return new SensitiveFieldPolicy(Set.copyOf(all));
    }

    public boolean isForbidden(String fieldName) {
        return forbidden.contains(normalize(fieldName));
    }

    private static String normalize(String name) {
        StringBuilder sb = new StringBuilder(name.length());
        for (char c : name.toLowerCase(Locale.ROOT).toCharArray()) {
            if (Character.isLetterOrDigit(c)) {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
