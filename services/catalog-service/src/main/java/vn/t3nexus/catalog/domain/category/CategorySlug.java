package vn.t3nexus.catalog.domain.category;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Phần chữ trong đường dẫn danh mục — tự sinh từ tên, chỉ để đọc/SEO; tra cứu luôn theo ID (service.md T-10).
 * Không duy nhất, không bất biến: đổi tên thì sinh lại.
 */
public final class CategorySlug {

    public static final int MAX_LENGTH = 100;

    // Tên toàn ký tự không chuyển được (VD chỉ có ký hiệu) vẫn cần 1 slug hợp lệ — URL vẫn tra được nhờ ID.
    private static final String FALLBACK = "danh-muc";

    private CategorySlug() {}

    /** "Điện thoại & Phụ kiện" → "dien-thoai-phu-kien". */
    public static String from(String name) {
        String ascii = Normalizer.normalize(name.replace('đ', 'd').replace('Đ', 'D'), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        String slug = ascii.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+)|(-+$)", "");
        if (slug.length() > MAX_LENGTH) {
            slug = slug.substring(0, MAX_LENGTH).replaceAll("-+$", "");
        }
        return slug.isEmpty() ? FALLBACK : slug;
    }
}
