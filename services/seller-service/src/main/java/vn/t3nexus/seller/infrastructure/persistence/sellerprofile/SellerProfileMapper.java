package vn.t3nexus.seller.infrastructure.persistence.sellerprofile;

import vn.t3nexus.seller.domain.sellerprofile.Contact;
import vn.t3nexus.seller.domain.sellerprofile.LegalInfo;
import vn.t3nexus.seller.domain.sellerprofile.SellerProfile;
import vn.t3nexus.seller.domain.sellerprofile.SellerProfileId;
import vn.t3nexus.seller.domain.sellerprofile.ShopInfo;

/**
 * Lớp tiện ích ánh xạ hai chiều giữa aggregate miền {@link SellerProfile} và thực thể JPA {@link SellerProfileJpaEntity}.
 */
public final class SellerProfileMapper {

    private SellerProfileMapper() {
    }

    /**
     * Dựng lại aggregate miền từ thực thể JPA.
     */
    public static SellerProfile toDomain(SellerProfileJpaEntity entity) {
        if (entity == null) {
            return null;
        }

        LegalInfo legalInfo = LegalInfo.of(
                entity.getSellerType(),
                entity.getLegalName(),
                entity.getRepresentativeName(),
                entity.getIdentityNumber(),
                entity.getTaxCode(),
                entity.getBusinessAddress()
        );

        Contact contact = Contact.of(
                entity.getContactName(),
                entity.getContactPhone(),
                entity.getContactAddress()
        );

        ShopInfo shopInfo = ShopInfo.of(
                entity.getShopName(),
                entity.getDescription(),
                contact
        );

        return SellerProfile.restore(
                SellerProfileId.of(entity.getId()),
                entity.getOwnerAccountId(),
                entity.getStatus(),
                legalInfo,
                shopInfo,
                entity.getRejectionReason(),
                entity.getSubmittedAt(),
                entity.getReviewedAt(),
                entity.getReviewedBy(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getVersion()
        );
    }

    /**
     * Tạo thực thể JPA mới từ aggregate miền (version = null để Hibernate nhận biết bản ghi mới).
     */
    public static SellerProfileJpaEntity toNewEntity(SellerProfile profile) {
        if (profile == null) {
            return null;
        }

        SellerProfileJpaEntity entity = new SellerProfileJpaEntity();
        entity.setId(profile.getId().getValue());
        entity.setOwnerAccountId(profile.ownerAccountId());
        entity.setStatus(profile.status());
        entity.setSellerType(profile.legalInfo().type());
        entity.setLegalName(profile.legalInfo().legalName());
        entity.setRepresentativeName(profile.legalInfo().representativeName());
        entity.setIdentityNumber(profile.legalInfo().identityNumber().value());
        entity.setTaxCode(profile.legalInfo().taxCode() != null ? profile.legalInfo().taxCode().value() : null);
        entity.setBusinessAddress(profile.legalInfo().businessAddress().value());
        entity.setIdentityKey(profile.legalInfo().identityKey());
        entity.setShopName(profile.shopInfo().shopName().display());
        entity.setShopNameKey(profile.shopInfo().shopName().key());
        entity.setDescription(profile.shopInfo().description());
        entity.setContactName(profile.shopInfo().contact().personName());
        entity.setContactPhone(profile.shopInfo().contact().phone().value());
        entity.setContactAddress(profile.shopInfo().contact().address() != null ? profile.shopInfo().contact().address().value() : null);
        entity.setRejectionReason(profile.rejectionReason());
        entity.setSubmittedAt(profile.submittedAt());
        entity.setReviewedAt(profile.reviewedAt());
        entity.setReviewedBy(profile.reviewedBy());
        entity.setVersion(null);
        entity.setCreatedAt(profile.createdAt());
        entity.setUpdatedAt(profile.updatedAt());
        return entity;
    }

    /**
     * Chép các trường có thể cập nhật từ aggregate nguồn vào thực thể JPA đích (giữ nguyên id, ownerAccountId, createdAt, version).
     */
    public static void copyInto(SellerProfileJpaEntity target, SellerProfile source) {
        target.setStatus(source.status());
        target.setSellerType(source.legalInfo().type());
        target.setLegalName(source.legalInfo().legalName());
        target.setRepresentativeName(source.legalInfo().representativeName());
        target.setIdentityNumber(source.legalInfo().identityNumber().value());
        target.setTaxCode(source.legalInfo().taxCode() != null ? source.legalInfo().taxCode().value() : null);
        target.setBusinessAddress(source.legalInfo().businessAddress().value());
        target.setIdentityKey(source.legalInfo().identityKey());
        target.setShopName(source.shopInfo().shopName().display());
        target.setShopNameKey(source.shopInfo().shopName().key());
        target.setDescription(source.shopInfo().description());
        target.setContactName(source.shopInfo().contact().personName());
        target.setContactPhone(source.shopInfo().contact().phone().value());
        target.setContactAddress(source.shopInfo().contact().address() != null ? source.shopInfo().contact().address().value() : null);
        target.setRejectionReason(source.rejectionReason());
        target.setSubmittedAt(source.submittedAt());
        target.setReviewedAt(source.reviewedAt());
        target.setReviewedBy(source.reviewedBy());
        target.setUpdatedAt(source.updatedAt());
    }
}
