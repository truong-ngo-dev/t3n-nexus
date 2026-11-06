package vn.t3nexus.seller.domain.sellerprofile;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Mã số thuế của người bán.
 */
public final class TaxCode {

    private static final Pattern P10 = Pattern.compile("^[0-9]{10}$");
    private static final Pattern P13 = Pattern.compile("^[0-9]{13}$");
    private static final Pattern P_DASH = Pattern.compile("^[0-9]{10}-[0-9]{3}$");

    private final String value;

    private TaxCode(String value) {
        this.value = value;
    }

    public static TaxCode of(String raw) {
        if (raw == null) {
            throw new SellerFieldException(SellerErrorCode.INVALID_TAX_CODE, "taxCode");
        }
        String cleaned = raw.replaceAll("\\s+", "");
        String digitsOnly;
        if (P10.matcher(cleaned).matches() || P13.matcher(cleaned).matches()) {
            digitsOnly = cleaned;
        } else if (P_DASH.matcher(cleaned).matches()) {
            digitsOnly = cleaned.replace("-", "");
        } else {
            throw new SellerFieldException(SellerErrorCode.INVALID_TAX_CODE, "taxCode");
        }
        return new TaxCode(digitsOnly);
    }

    public String value() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TaxCode taxCode = (TaxCode) o;
        return Objects.equals(value, taxCode.value);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(value);
    }

    @Override
    public String toString() {
        return "TaxCode[***]";
    }
}
