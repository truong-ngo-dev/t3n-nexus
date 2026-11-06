package vn.t3nexus.seller.domain.sellerprofile;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Địa chỉ của người bán hoặc người liên hệ.
 */
public final class Address {

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private final String value;

    private Address(String value) {
        this.value = value;
    }

    public static Address of(String raw, String field) {
        if (raw == null) {
            throw new SellerFieldException(SellerErrorCode.SELLER_FIELD_INVALID, field);
        }
        String stripped = raw.strip();
        String normalized = WHITESPACE.matcher(stripped).replaceAll(" ");
        if (normalized.length() < 10 || normalized.length() > 300) {
            throw new SellerFieldException(SellerErrorCode.SELLER_FIELD_INVALID, field);
        }
        return new Address(normalized);
    }

    public String value() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Address address = (Address) o;
        return Objects.equals(value, address.value);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(value);
    }

    @Override
    public String toString() {
        return "Address[***]";
    }
}
