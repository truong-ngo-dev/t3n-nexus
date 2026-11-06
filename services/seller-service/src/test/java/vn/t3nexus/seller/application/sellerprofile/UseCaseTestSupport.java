package vn.t3nexus.seller.application.sellerprofile;

import vn.t3nexus.lib.common.domain.service.IdGenerator;
import vn.t3nexus.seller.domain.sellerprofile.Contact;
import vn.t3nexus.seller.domain.sellerprofile.LegalInfo;
import vn.t3nexus.seller.domain.sellerprofile.SellerActor;
import vn.t3nexus.seller.domain.sellerprofile.SellerProfile;
import vn.t3nexus.seller.domain.sellerprofile.SellerProfileId;
import vn.t3nexus.seller.domain.sellerprofile.SellerProfileStatus;
import vn.t3nexus.seller.domain.sellerprofile.SellerType;
import vn.t3nexus.seller.domain.sellerprofile.ShopInfo;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Lớp tiện ích dùng chung cho các test use case hồ sơ người bán.
 */
public final class UseCaseTestSupport {

    public static final Instant NOW = Instant.parse("2026-10-26T03:00:00Z");

    private UseCaseTestSupport() {
    }

    public static Clock clock() {
        return Clock.fixed(NOW, ZoneOffset.UTC);
    }

    public static IdGenerator ids() {
        AtomicLong counter = new AtomicLong(0);
        return () -> new UUID(0, counter.incrementAndGet());
    }

    public static SellerActor user(UUID account) {
        return new SellerActor(account, "USER");
    }

    public static SellerActor admin(UUID account) {
        return new SellerActor(account, "ADMIN");
    }

    public static SubmitSellerProfile.Command submitCommand(SellerActor actor, String identityNumber, String shopName) {
        return new SubmitSellerProfile.Command(
                actor,
                SellerType.INDIVIDUAL,
                "Nguyễn Văn An",
                null,
                identityNumber,
                null,
                "12 Nguyễn Huệ, Quận 1, TP.HCM",
                shopName,
                "Mô tả gian hàng",
                "Nguyễn Văn An",
                "0912345678",
                null
        );
    }

    public static ResubmitSellerProfile.Command resubmitCommand(SellerActor actor, String identityNumber, String shopName) {
        return new ResubmitSellerProfile.Command(
                actor,
                SellerType.INDIVIDUAL,
                "Nguyễn Văn An",
                null,
                identityNumber,
                null,
                "12 Nguyễn Huệ, Quận 1, TP.HCM",
                shopName,
                "Mô tả gian hàng",
                "Nguyễn Văn An",
                "0912345678",
                null
        );
    }

    public static SellerProfile rejectedProfile(UUID owner, String identityNumber, String shopName) {
        LegalInfo legalInfo = LegalInfo.of(
                SellerType.INDIVIDUAL,
                "Nguyễn Văn An",
                null,
                identityNumber,
                null,
                "12 Nguyễn Huệ, Quận 1, TP.HCM"
        );
        ShopInfo shopInfo = ShopInfo.of(
                shopName,
                "Mô tả gian hàng",
                Contact.of("Nguyễn Văn An", "0912345678", null)
        );
        return SellerProfile.restore(
                SellerProfileId.of(new UUID(1, 1)),
                owner,
                SellerProfileStatus.REJECTED,
                legalInfo,
                shopInfo,
                "Giấy tờ không rõ",
                NOW,
                NOW.plusSeconds(60),
                new UUID(9, 9),
                NOW,
                NOW.plusSeconds(60),
                1L
        );
    }

    public static SellerProfile approvedProfile(UUID owner, String identityNumber, String shopName) {
        LegalInfo legalInfo = LegalInfo.of(
                SellerType.INDIVIDUAL,
                "Nguyễn Văn An",
                null,
                identityNumber,
                null,
                "12 Nguyễn Huệ, Quận 1, TP.HCM"
        );
        ShopInfo shopInfo = ShopInfo.of(
                shopName,
                "Mô tả gian hàng",
                Contact.of("Nguyễn Văn An", "0912345678", null)
        );
        return SellerProfile.restore(
                SellerProfileId.of(new UUID(1, 1)),
                owner,
                SellerProfileStatus.APPROVED,
                legalInfo,
                shopInfo,
                null,
                NOW,
                NOW.plusSeconds(60),
                new UUID(9, 9),
                NOW,
                NOW.plusSeconds(60),
                1L
        );
    }
}
