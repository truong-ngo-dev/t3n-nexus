package vn.t3nexus.seller.presentation.sellerprofile;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Đối tượng yêu cầu nộp hoặc nộp lại hồ sơ người bán.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SubmitSellerProfileRequest(
        String sellerType,
        LegalInfoRequest legalInfo,
        ShopRequest shop
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LegalInfoRequest(
            String legalName,
            String representativeName,
            String identityNumber,
            String taxCode,
            String businessAddress
    ) {
        @Override
        public String toString() {
            return "LegalInfoRequest[***]";
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ShopRequest(
            String shopName,
            String description,
            ContactRequest contact
    ) {
        @Override
        public String toString() {
            return "ShopRequest[***]";
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ContactRequest(
            String personName,
            String phone,
            String address
    ) {
        @Override
        public String toString() {
            return "ContactRequest[***]";
        }
    }

    @Override
    public String toString() {
        return "SubmitSellerProfileRequest[***]";
    }
}
