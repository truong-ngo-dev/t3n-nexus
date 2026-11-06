package vn.t3nexus.seller.application.sellerprofile;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.t3nexus.lib.common.domain.exception.DomainException;
import vn.t3nexus.seller.domain.sellerprofile.Contact;
import vn.t3nexus.seller.domain.sellerprofile.FakeSellerProfileRepository;
import vn.t3nexus.seller.domain.sellerprofile.LegalInfo;
import vn.t3nexus.seller.domain.sellerprofile.SellerErrorCode;
import vn.t3nexus.seller.domain.sellerprofile.SellerProfile;
import vn.t3nexus.seller.domain.sellerprofile.SellerProfileId;
import vn.t3nexus.seller.domain.sellerprofile.SellerType;
import vn.t3nexus.seller.domain.sellerprofile.ShopInfo;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static vn.t3nexus.seller.application.sellerprofile.UseCaseTestSupport.NOW;
import static vn.t3nexus.seller.application.sellerprofile.UseCaseTestSupport.user;

/**
 * Kiểm thử use case GetMySellerProfile.
 */
class GetMySellerProfileTest {

    private FakeSellerProfileRepository repository;
    private GetMySellerProfile getMySellerProfile;

    @BeforeEach
    void setUp() {
        repository = new FakeSellerProfileRepository();
        getMySellerProfile = new GetMySellerProfile(repository);
    }

    private SellerProfile saveProfile(UUID owner) {
        LegalInfo legalInfo = LegalInfo.of(
                SellerType.INDIVIDUAL,
                "Nguyễn Văn An",
                null,
                "079123456789",
                null,
                "12 Nguyễn Huệ, Quận 1, TP.HCM"
        );
        ShopInfo shopInfo = ShopInfo.of(
                "Gian Hàng Mẫu",
                "Mô tả",
                Contact.of("Nguyễn Văn An", "0912345678", null)
        );
        SellerProfile profile = SellerProfile.submit(
                SellerProfileId.of(UUID.randomUUID()),
                user(owner),
                legalInfo,
                shopInfo,
                NOW
        );
        repository.save(profile);
        return profile;
    }

    @Test
    @DisplayName("Có hồ sơ thì Result.profile trả về đúng hồ sơ của chủ sở hữu")
    void profileFoundReturnsOwnerProfile() {
        UUID owner = UUID.fromString("00000000-0000-0000-0000-000000000001");
        SellerProfile saved = saveProfile(owner);

        GetMySellerProfile.Query query = new GetMySellerProfile.Query(owner);
        GetMySellerProfile.Result result = getMySellerProfile.handle(query);

        assertThat(result.profile()).isNotNull();
        assertThat(result.profile().getId()).isEqualTo(saved.getId());
        assertThat(result.profile().ownerAccountId()).isEqualTo(owner);
    }

    @Test
    @DisplayName("Không có hồ sơ thì ném ngoại lệ SELLER_PROFILE_NOT_FOUND")
    void profileNotFoundThrowsDomainException() {
        UUID nonExistent = UUID.fromString("00000000-0000-0000-0000-000000000002");
        GetMySellerProfile.Query query = new GetMySellerProfile.Query(nonExistent);

        assertThatThrownBy(() -> getMySellerProfile.handle(query))
                .isInstanceOf(DomainException.class)
                .satisfies(ex -> assertThat(((DomainException) ex).getErrorCode().code())
                        .isEqualTo(SellerErrorCode.SELLER_PROFILE_NOT_FOUND.code()));
    }

    @Test
    @DisplayName("Hồ sơ của chủ A không bị lộ cho chủ B (B gọi trả về SELLER_PROFILE_NOT_FOUND)")
    void profileOfOwnerANotLeakedToOwnerB() {
        UUID ownerA = UUID.fromString("00000000-0000-0000-0000-00000000000A");
        UUID ownerB = UUID.fromString("00000000-0000-0000-0000-00000000000B");
        saveProfile(ownerA);

        GetMySellerProfile.Query queryB = new GetMySellerProfile.Query(ownerB);

        assertThatThrownBy(() -> getMySellerProfile.handle(queryB))
                .isInstanceOf(DomainException.class)
                .satisfies(ex -> assertThat(((DomainException) ex).getErrorCode().code())
                        .isEqualTo(SellerErrorCode.SELLER_PROFILE_NOT_FOUND.code()));
    }
}
