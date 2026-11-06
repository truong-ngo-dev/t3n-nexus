package vn.t3nexus.seller.domain.sellerprofile;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Số căn cước công dân của người bán.
 */
public final class IdentityNumber {

    private static final Pattern PATTERN = Pattern.compile("^[0-9]{12}$");

    private final String value;

    private IdentityNumber(String value) {
        this.value = value;
    }

    public static IdentityNumber of(String raw) {
        if (raw == null) {
            throw new SellerFieldException(SellerErrorCode.INVALID_IDENTITY_NUMBER, "identityNumber");
        }
        String cleaned = raw.replaceAll("\\s+", "");
        if (!PATTERN.matcher(cleaned).matches()) {
            throw new SellerFieldException(SellerErrorCode.INVALID_IDENTITY_NUMBER, "identityNumber");
        }
        return new IdentityNumber(cleaned);
    }

    public String value() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        IdentityNumber that = (IdentityNumber) o;
        return Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(value);
    }

    @Override
    public String toString() {
        return "IdentityNumber[***]";
    }
}
