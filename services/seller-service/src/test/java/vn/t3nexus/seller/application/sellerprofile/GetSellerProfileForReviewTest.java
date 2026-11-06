package vn.t3nexus.seller.application.sellerprofile;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.t3nexus.lib.common.domain.exception.DomainException;
import vn.t3nexus.seller.domain.sellerprofile.Contact;
import vn.t3nexus.seller.domain.sellerprofile.FakeSellerProfileRepository;
import vn.t3nexus.seller.domain.sellerprofile.LegalInfo;
import vn.t3nexus.seller.domain.sellerprofile.SellerActor;
import vn.t3nexus.seller.domain.sellerprofile.SellerErrorCode;
import vn.t3nexus.seller.domain.sellerprofile.SellerProfile;
import vn.t3nexus.seller.domain.sellerprofile.SellerProfileId;
import vn.t3nexus.seller.domain.sellerprofile.SellerProfileRepository;
import vn.t3nexus.seller.domain.sellerprofile.SellerProfileStatus;
import vn.t3nexus.seller.domain.sellerprofile.SellerType;
import vn.t3nexus.seller.domain.sellerprofile.ShopInfo;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static vn.t3nexus.seller.application.sellerprofile.UseCaseTestSupport.NOW;
import static vn.t3nexus.seller.application.sellerprofile.UseCaseTestSupport.admin;
import static vn.t3nexus.seller.application.sellerprofile.UseCaseTestSupport.user;

/**
 * Kiểm thử đơn vị use case GetSellerProfileForReview.
 */
class GetSellerProfileForReviewTest {

    private FakeSellerProfileRepository repository;
    private GetSellerProfileForReview getSellerProfileForReview;

    private static LegalInfo createLegalInfo() {
        return LegalInfo.of(
                SellerType.INDIVIDUAL,
                "Nguyễn Văn An",
                null,
                "079123456789",
                null,
                "12 Nguyễn Huệ, Quận 1, TP.HCM"
        );
    }

    private static ShopInfo createShopInfo() {
        return ShopInfo.of(
                "Gian Hàng Hoa Tươi",
                "Mô tả gian hàng",
                Contact.of("Nguyễn Văn An", "0912345678", null)
        );
    }

    @BeforeEach
    void setUp() {
        repository = new FakeSellerProfileRepository();
        getSellerProfileForReview = new GetSellerProfileForReview(repository);
    }

    @Test
    @DisplayName("ADMIN lấy được hồ sơ ở trạng thái PENDING_REVIEW")
    void adminGetsPendingReviewProfile() {
        UUID profileId = UUID.fromString("00000000-0000-0000-0000-000000000010");
        UUID ownerId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID adminId = UUID.fromString("00000000-0000-0000-0000-000000000002");

        SellerProfile profile = SellerProfile.restore(
                SellerProfileId.of(profileId),
                ownerId,
                SellerProfileStatus.PENDING_REVIEW,
                createLegalInfo(),
                createShopInfo(),
                null,
                NOW,
                null,
                null,
                NOW,
                NOW,
                3L
        );
        repository.save(profile);

        GetSellerProfileForReview.Query q = new GetSellerProfileForReview.Query(admin(adminId), profileId);
        GetSellerProfileForReview.Result result = getSellerProfileForReview.handle(q);

        assertThat(result.profile()).isNotNull();
        assertThat(result.profile().status()).isEqualTo(SellerProfileStatus.PENDING_REVIEW);
        assertThat(result.profile().version()).isEqualTo(3L);
        assertThat(result.profile().getId()).isEqualTo(SellerProfileId.of(profileId));
    }

    @Test
    @DisplayName("ADMIN lấy được hồ sơ ở trạng thái APPROVED")
    void adminGetsApprovedProfile() {
        UUID profileId = UUID.fromString("00000000-0000-0000-0000-000000000010");
        UUID ownerId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID adminId = UUID.fromString("00000000-0000-0000-0000-000000000002");

        SellerProfile profile = SellerProfile.restore(
                SellerProfileId.of(profileId),
                ownerId,
                SellerProfileStatus.APPROVED,
                createLegalInfo(),
                createShopInfo(),
                null,
                NOW,
                NOW.plusSeconds(60),
                adminId,
                NOW,
                NOW.plusSeconds(60),
                4L
        );
        repository.save(profile);

        GetSellerProfileForReview.Query q = new GetSellerProfileForReview.Query(admin(adminId), profileId);
        GetSellerProfileForReview.Result result = getSellerProfileForReview.handle(q);

        assertThat(result.profile()).isNotNull();
        assertThat(result.profile().status()).isEqualTo(SellerProfileStatus.APPROVED);
        assertThat(result.profile().version()).isEqualTo(4L);
        assertThat(result.profile().getId()).isEqualTo(SellerProfileId.of(profileId));
    }

    @Test
    @DisplayName("ADMIN lấy được hồ sơ ở trạng thái REJECTED")
    void adminGetsRejectedProfile() {
        UUID profileId = UUID.fromString("00000000-0000-0000-0000-000000000010");
        UUID ownerId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID adminId = UUID.fromString("00000000-0000-0000-0000-000000000002");

        SellerProfile profile = SellerProfile.restore(
                SellerProfileId.of(profileId),
                ownerId,
                SellerProfileStatus.REJECTED,
                createLegalInfo(),
                createShopInfo(),
                "Giấy tờ không rõ",
                NOW,
                NOW.plusSeconds(60),
                adminId,
                NOW,
                NOW.plusSeconds(60),
                4L
        );
        repository.save(profile);

        GetSellerProfileForReview.Query q = new GetSellerProfileForReview.Query(admin(adminId), profileId);
        GetSellerProfileForReview.Result result = getSellerProfileForReview.handle(q);

        assertThat(result.profile()).isNotNull();
        assertThat(result.profile().status()).isEqualTo(SellerProfileStatus.REJECTED);
        assertThat(result.profile().version()).isEqualTo(4L);
        assertThat(result.profile().getId()).isEqualTo(SellerProfileId.of(profileId));
    }

    @Test
    @DisplayName("Hồ sơ không tồn tại ném SELLER_PROFILE_NOT_FOUND")
    void profileNotFoundThrows() {
        UUID profileId = UUID.fromString("00000000-0000-0000-0000-000000000099");
        UUID adminId = UUID.fromString("00000000-0000-0000-0000-000000000002");

        GetSellerProfileForReview.Query q = new GetSellerProfileForReview.Query(admin(adminId), profileId);

        assertThatThrownBy(() -> getSellerProfileForReview.handle(q))
                .isInstanceOf(DomainException.class)
                .satisfies(ex -> assertThat(((DomainException) ex).getErrorCode())
                        .isEqualTo(SellerErrorCode.SELLER_PROFILE_NOT_FOUND));
    }

    @Test
    @DisplayName("USER bị ném SELLER_ROLE_NOT_ALLOWED và verifyNoInteractions với kho")
    void userRoleNotAllowed() {
        SellerProfileRepository mockRepo = mock(SellerProfileRepository.class);
        GetSellerProfileForReview useCase = new GetSellerProfileForReview(mockRepo);

        UUID profileId = UUID.fromString("00000000-0000-0000-0000-000000000010");
        UUID userId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        GetSellerProfileForReview.Query q = new GetSellerProfileForReview.Query(user(userId), profileId);

        assertThatThrownBy(() -> useCase.handle(q))
                .isInstanceOf(DomainException.class)
                .satisfies(ex -> assertThat(((DomainException) ex).getErrorCode())
                        .isEqualTo(SellerErrorCode.SELLER_ROLE_NOT_ALLOWED));

        verifyNoInteractions(mockRepo);
    }

    @Test
    @DisplayName("Vai rỗng bị ném SELLER_ROLE_NOT_ALLOWED và verifyNoInteractions với kho")
    void emptyRoleNotAllowed() {
        SellerProfileRepository mockRepo = mock(SellerProfileRepository.class);
        GetSellerProfileForReview useCase = new GetSellerProfileForReview(mockRepo);

        UUID profileId = UUID.fromString("00000000-0000-0000-0000-000000000010");
        UUID actorId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        GetSellerProfileForReview.Query q = new GetSellerProfileForReview.Query(new SellerActor(actorId, ""), profileId);

        assertThatThrownBy(() -> useCase.handle(q))
                .isInstanceOf(DomainException.class)
                .satisfies(ex -> assertThat(((DomainException) ex).getErrorCode())
                        .isEqualTo(SellerErrorCode.SELLER_ROLE_NOT_ALLOWED));

        verifyNoInteractions(mockRepo);
    }
}
