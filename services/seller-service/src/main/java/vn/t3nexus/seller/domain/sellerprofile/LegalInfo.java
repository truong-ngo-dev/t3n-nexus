package vn.t3nexus.seller.domain.sellerprofile;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Thông tin pháp lý của người bán.
 */
public final class LegalInfo {

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private final SellerType type;
    private final String legalName;
    private final String representativeName;
    private final IdentityNumber identityNumber;
    private final TaxCode taxCode;
    private final Address businessAddress;

    private LegalInfo(
            SellerType type,
            String legalName,
            String representativeName,
            IdentityNumber identityNumber,
            TaxCode taxCode,
            Address businessAddress
    ) {
        this.type = type;
        this.legalName = legalName;
        this.representativeName = representativeName;
        this.identityNumber = identityNumber;
        this.taxCode = taxCode;
        this.businessAddress = businessAddress;
    }

    public static LegalInfo of(
            SellerType type,
            String legalName,
            String representativeName,
            String identityNumber,
            String taxCode,
            String businessAddress
    ) {
        // 0. type null thì SELLER_FIELD_INVALID field sellerType
        if (type == null) {
            throw new SellerFieldException(SellerErrorCode.SELLER_FIELD_INVALID, "sellerType");
        }

        // 1. legalName (bắt buộc mọi loại): null/toàn khoảng trắng thì LEGAL_INFO_REQUIRED field legalName;
        // strip() + gộp khoảng trắng; độ dài 2 đến 150 ngoài thì SELLER_FIELD_INVALID field legalName.
        if (legalName == null || legalName.isBlank()) {
            throw new SellerFieldException(SellerErrorCode.LEGAL_INFO_REQUIRED, "legalName");
        }
        String strippedLegalName = legalName.strip();
        String normalizedLegalName = WHITESPACE.matcher(strippedLegalName).replaceAll(" ");
        if (normalizedLegalName.length() < 2 || normalizedLegalName.length() > 150) {
            throw new SellerFieldException(SellerErrorCode.SELLER_FIELD_INVALID, "legalName");
        }

        // 2. representativeName: chỉ với BUSINESS: bắt buộc (null/trắng thì LEGAL_INFO_REQUIRED field representativeName),
        // strip() + gộp khoảng trắng, độ dài 2 đến 100 (ngoài thì SELLER_FIELD_INVALID field representativeName).
        // Với INDIVIDUAL và HOUSEHOLD: BỎ QUA giá trị truyền vào, lưu null.
        String normalizedRepName = null;
        if (type == SellerType.BUSINESS) {
            if (representativeName == null || representativeName.isBlank()) {
                throw new SellerFieldException(SellerErrorCode.LEGAL_INFO_REQUIRED, "representativeName");
            }
            String strippedRepName = representativeName.strip();
            normalizedRepName = WHITESPACE.matcher(strippedRepName).replaceAll(" ");
            if (normalizedRepName.length() < 2 || normalizedRepName.length() > 100) {
                throw new SellerFieldException(SellerErrorCode.SELLER_FIELD_INVALID, "representativeName");
            }
        }

        // 3. identityNumber (bắt buộc mọi loại): null/trắng thì LEGAL_INFO_REQUIRED field identityNumber; ngược lại IdentityNumber.of.
        if (identityNumber == null || identityNumber.isBlank()) {
            throw new SellerFieldException(SellerErrorCode.LEGAL_INFO_REQUIRED, "identityNumber");
        }
        IdentityNumber identityNumberVo = IdentityNumber.of(identityNumber);

        // 4. taxCode: bắt buộc với HOUSEHOLD và BUSINESS (null/trắng thì LEGAL_INFO_REQUIRED field taxCode);
        // với INDIVIDUAL là tùy chọn: null/trắng thì lưu null, có giá trị thì phải hợp lệ qua TaxCode.of.
        // Với HOUSEHOLD và BUSINESS có giá trị thì qua TaxCode.of.
        TaxCode taxCodeVo = null;
        if (type == SellerType.HOUSEHOLD || type == SellerType.BUSINESS) {
            if (taxCode == null || taxCode.isBlank()) {
                throw new SellerFieldException(SellerErrorCode.LEGAL_INFO_REQUIRED, "taxCode");
            }
            taxCodeVo = TaxCode.of(taxCode);
        } else {
            if (taxCode != null && !taxCode.isBlank()) {
                taxCodeVo = TaxCode.of(taxCode);
            }
        }

        // 5. businessAddress (bắt buộc mọi loại): null/trắng thì LEGAL_INFO_REQUIRED field businessAddress;
        // ngược lại Address.of(businessAddress, "businessAddress").
        if (businessAddress == null || businessAddress.isBlank()) {
            throw new SellerFieldException(SellerErrorCode.LEGAL_INFO_REQUIRED, "businessAddress");
        }
        Address businessAddressVo = Address.of(businessAddress, "businessAddress");

        return new LegalInfo(type, normalizedLegalName, normalizedRepName, identityNumberVo, taxCodeVo, businessAddressVo);
    }

    public SellerType type() {
        return type;
    }

    public String legalName() {
        return legalName;
    }

    public String representativeName() {
        return representativeName;
    }

    public IdentityNumber identityNumber() {
        return identityNumber;
    }

    public TaxCode taxCode() {
        return taxCode;
    }

    public Address businessAddress() {
        return businessAddress;
    }

    public String identityKey() {
        if (type == SellerType.INDIVIDUAL) {
            return identityNumber.value();
        }
        return taxCode.value();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LegalInfo legalInfo = (LegalInfo) o;
        return type == legalInfo.type &&
                Objects.equals(legalName, legalInfo.legalName) &&
                Objects.equals(representativeName, legalInfo.representativeName) &&
                Objects.equals(identityNumber, legalInfo.identityNumber) &&
                Objects.equals(taxCode, legalInfo.taxCode) &&
                Objects.equals(businessAddress, legalInfo.businessAddress);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, legalName, representativeName, identityNumber, taxCode, businessAddress);
    }

    @Override
    public String toString() {
        return "LegalInfo[type=" + type + "]";
    }
}
