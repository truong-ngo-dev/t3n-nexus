package vn.t3nexus.seller.presentation.sellerprofile;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import vn.t3nexus.seller.domain.sellerprofile.Contact;
import vn.t3nexus.seller.domain.sellerprofile.LegalInfo;
import vn.t3nexus.seller.domain.sellerprofile.SellerProfile;
import vn.t3nexus.seller.domain.sellerprofile.ShopInfo;

import java.time.Instant;
import java.util.UUID;

/**
 * Đối tượng phản hồi thông tin chi tiết hồ sơ người bán cho quản trị viên.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AdminSellerProfileResponse(
        UUID id,
        UUID ownerAccountId,
        String status,
        Long version,
        String sellerType,
        SellerProfileResponse.LegalInfoResponse legalInfo,
        SellerProfileResponse.ShopResponse shop,
        String rejectionReason,
        Instant submittedAt,
        Instant reviewedAt,
        UUID reviewedBy
) {

    public static AdminSellerProfileResponse from(SellerProfile profile) {
        return from(profile, profile.version());
    }

    /** Dựng phản hồi với phiên bản chỉ định (phiên bản sau quyết định, vì hồ sơ chỉ giữ phiên bản đã nạp). */
    public static AdminSellerProfileResponse from(SellerProfile profile, long version) {
        LegalInfo legal = profile.legalInfo();
        SellerProfileResponse.LegalInfoResponse legalResponse = new SellerProfileResponse.LegalInfoResponse(
                legal.legalName(),
                legal.representativeName(),
                legal.identityNumber() != null ? legal.identityNumber().value() : null,
                legal.taxCode() != null ? legal.taxCode().value() : null,
                legal.businessAddress() != null ? legal.businessAddress().value() : null
        );

        ShopInfo shop = profile.shopInfo();
        Contact contact = shop.contact();
        SellerProfileResponse.ContactResponse contactResponse = new SellerProfileResponse.ContactResponse(
                contact.personName(),
                contact.phone() != null ? contact.phone().value() : null,
                contact.address() != null ? contact.address().value() : null
        );

        SellerProfileResponse.ShopResponse shopResponse = new SellerProfileResponse.ShopResponse(
                shop.shopName().display(),
                shop.description(),
                contactResponse
        );

        return new AdminSellerProfileResponse(
                profile.getId().getValue(),
                profile.ownerAccountId(),
                profile.status().name(),
                version,
                legal.type().name(),
                legalResponse,
                shopResponse,
                profile.rejectionReason(),
                profile.submittedAt(),
                profile.reviewedAt(),
                profile.reviewedBy()
        );
    }

    @Override
    public String toString() {
        return "AdminSellerProfileResponse[***]";
    }
}
