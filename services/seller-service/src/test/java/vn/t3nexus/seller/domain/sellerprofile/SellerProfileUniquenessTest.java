package vn.t3nexus.seller.domain.sellerprofile;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.t3nexus.lib.common.domain.exception.DomainException;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Kiểm thử domain service SellerProfileUniqueness.
 */
class SellerProfileUniquenessTest {

    private static final Instant NOW = Instant.parse("2026-10-26T03:04:05.123456789Z");
    private static final UUID OWNER_A = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OWNER_B = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private FakeSellerProfileRepository repository;
    private SellerProfileUniqueness uniqueness;

    @BeforeEach
    void setUp() {
        repository = new FakeSellerProfileRepository();
        uniqueness = new SellerProfileUniqueness(repository);
    }

    private LegalInfo createLegalInfo(SellerType type, String identityNumber, String taxCode) {
        return LegalInfo.of(
                type,
                "Chủ thể kinh doanh",
                type == SellerType.BUSINESS ? "Đại diện" : null,
                identityNumber,
                taxCode,
                "Số 1 Đại Cồ Việt, Hai Bà Trưng, Hà Nội"
        );
    }

    private ShopInfo createShopInfo(String shopName) {
        return ShopInfo.of(
                shopName,
                "Mô tả",
                Contact.of("Người liên hệ", "0912345678", null)
        );
    }

    @Test
    void requireNoProfileThrowsWhenProfileExistsRegardlessOfStatus() {
        // requireNoProfile êm khi chưa có
        uniqueness.requireNoProfile(OWNER_A);

        // ném SELLER_PROFILE_EXISTS khi chủ đã có hồ sơ (kể cả REJECTED)
        SellerProfile rejectedProfile = SellerProfile.restore(
                SellerProfileId.of(UUID.randomUUID()),
                OWNER_A,
                SellerProfileStatus.REJECTED,
                createLegalInfo(SellerType.INDIVIDUAL, "079123456789", null),
                createShopInfo("Shop A"),
                "Từ chối", NOW, NOW, UUID.randomUUID(), NOW, NOW, 1L
        );
        repository.save(rejectedProfile);

        assertThatThrownBy(() -> uniqueness.requireNoProfile(OWNER_A))
                .isInstanceOf(DomainException.class)
                .satisfies(ex -> assertThat(((DomainException) ex).getErrorCode())
                        .isEqualTo(SellerErrorCode.SELLER_PROFILE_EXISTS));
    }

    @Test
    void requireShopNameAvailableValidations() {
        ShopName shopName = ShopName.of("Cửa Hàng Xanh");

        // chưa ai dùng thì êm
        uniqueness.requireShopNameAvailable(OWNER_A, shopName);

        // Lưu hồ sơ của OWNER_A với tên này
        SellerProfile profileA = SellerProfile.restore(
                SellerProfileId.of(UUID.randomUUID()),
                OWNER_A,
                SellerProfileStatus.APPROVED,
                createLegalInfo(SellerType.INDIVIDUAL, "079123456789", null),
                createShopInfo("Cửa Hàng Xanh"),
                null, NOW, NOW, UUID.randomUUID(), NOW, NOW, 1L
        );
        repository.save(profileA);

        // cùng chủ (tự sửa tên của chính mình) thì êm
        uniqueness.requireShopNameAvailable(OWNER_A, ShopName.of("CỬA HÀNG XANH"));

        // tên trùng khóa với hồ sơ của chủ khác (khác dấu, khác hoa) ném SHOP_NAME_TAKEN
        ShopName duplicateNameDifferentCase = ShopName.of("cua hang xanh");
        assertThatThrownBy(() -> uniqueness.requireShopNameAvailable(OWNER_B, duplicateNameDifferentCase))
                .isInstanceOf(DomainException.class)
                .satisfies(ex -> assertThat(((DomainException) ex).getErrorCode())
                        .isEqualTo(SellerErrorCode.SHOP_NAME_TAKEN));
    }

    @Test
    void requireIdentityAvailableValidations() {
        String idNumber = "079123456789";
        LegalInfo legalInfo = createLegalInfo(SellerType.INDIVIDUAL, idNumber, null);

        // chưa ai dùng thì êm
        uniqueness.requireIdentityAvailable(OWNER_A, legalInfo);

        // OWNER_A có hồ sơ PENDING_REVIEW
        SellerProfile profileA = SellerProfile.restore(
                SellerProfileId.of(UUID.randomUUID()),
                OWNER_A,
                SellerProfileStatus.PENDING_REVIEW,
                legalInfo,
                createShopInfo("Shop A"),
                null, NOW, null, null, NOW, NOW, 1L
        );
        repository.save(profileA);

        // cùng chủ thì êm
        uniqueness.requireIdentityAvailable(OWNER_A, legalInfo);

        // khóa trùng hồ sơ PENDING_REVIEW của chủ khác ném IDENTITY_ALREADY_REGISTERED
        // và thông báo của IDENTITY_ALREADY_REGISTERED không chứa khóa định danh
        assertThatThrownBy(() -> uniqueness.requireIdentityAvailable(OWNER_B, legalInfo))
                .isInstanceOf(DomainException.class)
                .satisfies(ex -> {
                    DomainException de = (DomainException) ex;
                    assertThat(de.getErrorCode()).isEqualTo(SellerErrorCode.IDENTITY_ALREADY_REGISTERED);
                    assertThat(de.getMessage()).doesNotContain(idNumber);
                });

        // trùng hồ sơ APPROVED của chủ khác ném IDENTITY_ALREADY_REGISTERED
        SellerProfile approvedProfile = SellerProfile.restore(
                SellerProfileId.of(UUID.randomUUID()),
                OWNER_A,
                SellerProfileStatus.APPROVED,
                legalInfo,
                createShopInfo("Shop A"),
                null, NOW, NOW, UUID.randomUUID(), NOW, NOW, 1L
        );
        repository.save(approvedProfile);

        assertThatThrownBy(() -> uniqueness.requireIdentityAvailable(OWNER_B, legalInfo))
                .isInstanceOf(DomainException.class)
                .satisfies(ex -> {
                    DomainException de = (DomainException) ex;
                    assertThat(de.getErrorCode()).isEqualTo(SellerErrorCode.IDENTITY_ALREADY_REGISTERED);
                    assertThat(de.getMessage()).doesNotContain(idNumber);
                });

        // trùng hồ sơ REJECTED của chủ khác thì êm
        SellerProfile rejectedProfile = SellerProfile.restore(
                SellerProfileId.of(UUID.randomUUID()),
                OWNER_A,
                SellerProfileStatus.REJECTED,
                legalInfo,
                createShopInfo("Shop A"),
                "Lý do", NOW, NOW, UUID.randomUUID(), NOW, NOW, 1L
        );
        repository.save(rejectedProfile);

        uniqueness.requireIdentityAvailable(OWNER_B, legalInfo);
    }

    @Test
    void requireCanSubmitOrderOfChecks() {
        // OWNER_A đã có hồ sơ, tên "Shop A", CCCD "079123456789"
        LegalInfo legalA = createLegalInfo(SellerType.INDIVIDUAL, "079123456789", null);
        ShopInfo shopA = createShopInfo("Shop A");
        SellerProfile profileA = SellerProfile.restore(
                SellerProfileId.of(UUID.randomUUID()),
                OWNER_A,
                SellerProfileStatus.APPROVED,
                legalA,
                shopA,
                null, NOW, NOW, UUID.randomUUID(), NOW, NOW, 1L
        );
        repository.save(profileA);

        // khi vừa đã có hồ sơ vừa trùng tên thì lỗi là SELLER_PROFILE_EXISTS
        // (OWNER_A nộp lại với tên trùng Shop A của chính mình)
        assertThatThrownBy(() -> uniqueness.requireCanSubmit(OWNER_A, shopA.shopName(), legalA))
                .isInstanceOf(DomainException.class)
                .satisfies(ex -> assertThat(((DomainException) ex).getErrorCode())
                        .isEqualTo(SellerErrorCode.SELLER_PROFILE_EXISTS));

        // khi vừa trùng tên vừa trùng khóa thì lỗi là SHOP_NAME_TAKEN
        // (OWNER_B chưa có hồ sơ, nộp tên trùng Shop A và CCCD trùng OWNER_A)
        assertThatThrownBy(() -> uniqueness.requireCanSubmit(OWNER_B, shopA.shopName(), legalA))
                .isInstanceOf(DomainException.class)
                .satisfies(ex -> assertThat(((DomainException) ex).getErrorCode())
                        .isEqualTo(SellerErrorCode.SHOP_NAME_TAKEN));
    }

    @Test
    void repositoryDeleteThrowsUnsupportedOperationException() {
        // kiểm SellerProfileRepository.delete(...) (gọi qua fake) ném UnsupportedOperationException
        assertThatThrownBy(() -> repository.delete(SellerProfileId.of(UUID.randomUUID())))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
