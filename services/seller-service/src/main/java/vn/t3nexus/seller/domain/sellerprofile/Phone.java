package vn.t3nexus.seller.domain.sellerprofile;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Số điện thoại người liên hệ.
 */
public final class Phone {

    private static final Pattern PATTERN = Pattern.compile("^0[35789][0-9]{8}$");

    private final String value;

    private Phone(String value) {
        this.value = value;
    }

    public static Phone of(String raw) {
        if (raw == null) {
            throw new SellerFieldException(SellerErrorCode.SELLER_FIELD_INVALID, "contact.phone");
        }
        String cleaned = raw.replaceAll("[\\s.\\-()]", "");
        if (cleaned.startsWith("+84")) {
            cleaned = "0" + cleaned.substring(3);
        } else if (cleaned.startsWith("84") && cleaned.length() == 11) {
            cleaned = "0" + cleaned.substring(2);
        }
        if (!PATTERN.matcher(cleaned).matches()) {
            throw new SellerFieldException(SellerErrorCode.SELLER_FIELD_INVALID, "contact.phone");
        }
        return new Phone(cleaned);
    }

    public String value() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Phone phone = (Phone) o;
        return Objects.equals(value, phone.value);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(value);
    }

    @Override
    public String toString() {
        return "Phone[***]";
    }
}
