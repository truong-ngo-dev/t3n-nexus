package vn.t3nexus.seller.presentation.sellerprofile;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import vn.t3nexus.seller.domain.sellerprofile.Contact;
import vn.t3nexus.seller.domain.sellerprofile.LegalInfo;
import vn.t3nexus.seller.domain.sellerprofile.SellerProfile;
import vn.t3nexus.seller.domain.sellerprofile.ShopInfo;

import java.time.Instant;
import java.util.UUID;

/**
 * Đối tượng dữ liệu phản hồi thông tin hồ sơ người bán.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SellerProfileResponse(
        UUID id,
        String status,
        String sellerType,
        LegalInfoResponse legalInfo,
        ShopResponse shop,
        String rejectionReason,
        Instant submittedAt
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LegalInfoResponse(
            String legalName,
            String representativeName,
            String identityNumber,
            String taxCode,
            String businessAddress
    ) {
        @Override
        public String toString() {
            return "LegalInfoResponse[***]";
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ShopResponse(
            String shopName,
            String description,
            ContactResponse contact
    ) {
        @Override
        public String toString() {
            return "ShopResponse[***]";
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ContactResponse(
            String personName,
            String phone,
            String address
    ) {
        @Override
        public String toString() {
            return "ContactResponse[***]";
        }
    }

    public static SellerProfileResponse from(SellerProfile profile) {
        LegalInfo legal = profile.legalInfo();
        LegalInfoResponse legalResponse = new LegalInfoResponse(
                legal.legalName(),
                legal.representativeName(),
                legal.identityNumber() != null ? legal.identityNumber().value() : null,
                legal.taxCode() != null ? legal.taxCode().value() : null,
                legal.businessAddress() != null ? legal.businessAddress().value() : null
        );

        ShopInfo shop = profile.shopInfo();
        Contact contact = shop.contact();
        ContactResponse contactResponse = new ContactResponse(
                contact.personName(),
                contact.phone() != null ? contact.phone().value() : null,
                contact.address() != null ? contact.address().value() : null
        );

        ShopResponse shopResponse = new ShopResponse(
                shop.shopName().display(),
                shop.description(),
                contactResponse
        );

        return new SellerProfileResponse(
                profile.getId().getValue(),
                profile.status().name(),
                legal.type().name(),
                legalResponse,
                shopResponse,
                profile.rejectionReason(),
                profile.submittedAt()
        );
    }

    @Override
    public String toString() {
        return "SellerProfileResponse[***]";
    }
}
