package vn.t3nexus.seller.domain.sellerprofile;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Tên gian hàng người bán.
 */
public final class ShopName {

    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private final String display;
    private final String key;

    private ShopName(String display, String key) {
        this.display = display;
        this.key = key;
    }

    public static ShopName of(String raw) {
        if (raw == null) {
            throw new SellerFieldException(SellerErrorCode.SELLER_FIELD_INVALID, "shopName");
        }
        String stripped = raw.strip();
        String display = WHITESPACE.matcher(stripped).replaceAll(" ");
        if (display.length() < 3 || display.length() > 60) {
            throw new SellerFieldException(SellerErrorCode.SELLER_FIELD_INVALID, "shopName");
        }
        String normalized = Normalizer.normalize(display, Normalizer.Form.NFD);
        String withoutDiacritics = DIACRITICS.matcher(normalized).replaceAll("");
        String key = withoutDiacritics.replace('đ', 'd').replace('Đ', 'D').toLowerCase(Locale.ROOT);
        return new ShopName(display, key);
    }

    public String display() {
        return display;
    }

    public String key() {
        return key;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ShopName shopName = (ShopName) o;
        return Objects.equals(key, shopName.key);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(key);
    }

    @Override
    public String toString() {
        return display;
    }
}
