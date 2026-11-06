package vn.t3nexus.seller.infrastructure.persistence.sellerprofile;

import vn.t3nexus.seller.domain.sellerprofile.Contact;
import vn.t3nexus.seller.domain.sellerprofile.LegalInfo;
import vn.t3nexus.seller.domain.sellerprofile.SellerActor;
import vn.t3nexus.seller.domain.sellerprofile.SellerProfile;
import vn.t3nexus.seller.domain.sellerprofile.SellerProfileId;
import vn.t3nexus.seller.domain.sellerprofile.SellerProfileStatus;
import vn.t3nexus.seller.domain.sellerprofile.SellerType;
import vn.t3nexus.seller.domain.sellerprofile.ShopInfo;

import java.time.Instant;
import java.util.UUID;

/**
 * Lớp tiện ích dựng giá trị mẫu hợp lệ cho kiểm thử kho lưu trữ hồ sơ người bán.
 */
final class SellerProfileSamples {

    static final Instant NOW = Instant.parse("2026-10-26T03:00:00Z");

    private SellerProfileSamples() {
    }

    static SellerActor actorOf(UUID ownerAccountId) {
        return new SellerActor(ownerAccountId, "USER");
    }

    static LegalInfo individual(String identityNumber) {
        return LegalInfo.of(
                SellerType.INDIVIDUAL,
                "Nguyễn Văn An",
                null,
                identityNumber,
                null,
                "12 Nguyễn Huệ, Quận 1, TP.HCM"
        );
    }

    static LegalInfo household(String taxCode) {
        return LegalInfo.of(
                SellerType.HOUSEHOLD,
                "Trần Thị Bình",
                null,
                "079123456789",
                taxCode,
                "12 Nguyễn Huệ, Quận 1, TP.HCM"
        );
    }

    static LegalInfo business(String taxCode) {
        return LegalInfo.of(
                SellerType.BUSINESS,
                "Công ty TNHH Sao Mai",
                "Lê Quang Cường",
                "079123456789",
                taxCode,
                "Tòa nhà ABC, 99 Lê Lợi, Quận 1, TP.HCM"
        );
    }

    static ShopInfo shop(String shopName) {
        return ShopInfo.of(
                shopName,
                "Mô tả gian hàng",
                Contact.of("Nguyễn Văn An", "0912345678", null)
        );
    }

    static ShopInfo shopWithContactAddress(String shopName) {
        return ShopInfo.of(
                shopName,
                null,
                Contact.of("Nguyễn Văn An", "0912345678", "34 Hai Bà Trưng, Quận 3, TP.HCM")
        );
    }

    static SellerProfile submit(UUID owner, LegalInfo legal, ShopInfo shop) {
        return SellerProfile.submit(
                SellerProfileId.of(UUID.randomUUID()),
                actorOf(owner),
                legal,
                shop,
                NOW
        );
    }

    static SellerProfile rejected(UUID owner, LegalInfo legal, ShopInfo shop) {
        return SellerProfile.restore(
                SellerProfileId.of(UUID.randomUUID()),
                owner,
                SellerProfileStatus.REJECTED,
                legal,
                shop,
                "Giấy tờ không rõ",
                NOW,
                NOW.plusSeconds(60),
                UUID.randomUUID(),
                NOW,
                NOW.plusSeconds(60),
                1L
        );
    }

    static String digits(int n, int width) {
        return String.format("%0" + width + "d", n);
    }
}
