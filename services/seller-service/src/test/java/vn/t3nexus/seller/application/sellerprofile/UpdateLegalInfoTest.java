package vn.t3nexus.seller.application.sellerprofile;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.t3nexus.lib.common.domain.exception.DomainException;
import vn.t3nexus.seller.application.sellerprofile.shared.Patch;
import vn.t3nexus.seller.domain.sellerprofile.Contact;
import vn.t3nexus.seller.domain.sellerprofile.FakeSellerProfileRepository;
import vn.t3nexus.seller.domain.sellerprofile.LegalInfo;
import vn.t3nexus.seller.domain.sellerprofile.SellerErrorCode;
import vn.t3nexus.seller.domain.sellerprofile.SellerProfile;
import vn.t3nexus.seller.domain.sellerprofile.SellerProfileId;
import vn.t3nexus.seller.domain.sellerprofile.SellerProfileRepository;
import vn.t3nexus.seller.domain.sellerprofile.SellerType;
import vn.t3nexus.seller.domain.sellerprofile.ShopInfo;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static vn.t3nexus.seller.application.sellerprofile.UseCaseTestSupport.NOW;
import static vn.t3nexus.seller.application.sellerprofile.UseCaseTestSupport.admin;
import static vn.t3nexus.seller.application.sellerprofile.UseCaseTestSupport.approvedProfile;
import static vn.t3nexus.seller.application.sellerprofile.UseCaseTestSupport.clock;
import static vn.t3nexus.seller.application.sellerprofile.UseCaseTestSupport.rejectedProfile;
import static vn.t3nexus.seller.application.sellerprofile.UseCaseTestSupport.user;

/**
 * Kiểm thử use case UpdateLegalInfo.
 */
class UpdateLegalInfoTest {

    private FakeSellerProfileRepository fakeRepository;
    private CountingSellerProfileRepository countingRepository;
    private UpdateLegalInfo updateLegalInfo;

    @BeforeEach
    void setUp() {
        fakeRepository = new FakeSellerProfileRepository();
        countingRepository = new CountingSellerProfileRepository(fakeRepository);
        updateLegalInfo = new UpdateLegalInfo(countingRepository, clock());
    }

    private SellerProfile createIndividualProfile(UUID owner, String identityNumber, String taxCode) {
        LegalInfo legalInfo = LegalInfo.of(
                SellerType.INDIVIDUAL,
                "Nguyễn Văn An",
                null,
                identityNumber,
                taxCode,
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
                UUID.randomUUID(),
                NOW.minusSeconds(3600)
        );
        fakeRepository.save(profile);
        countingRepository.resetSaveCount();
        return profile;
    }

    @Test
    @DisplayName("Các trường absent giữ nguyên giá trị hiện tại khi chỉ đổi legalName")
    void absentFieldsKeepCurrentValues() {
        UUID owner = UUID.fromString("00000000-0000-0000-0000-000000000001");
        createIndividualProfile(owner, "079123456789", null);

        UpdateLegalInfo.Command cmd = new UpdateLegalInfo.Command(
                user(owner),
                Patch.absent(),
                Patch.of("Trần Văn Bình"),
                Patch.absent(),
                Patch.absent(),
                Patch.absent(),
                Patch.absent()
        );

        UpdateLegalInfo.Result result = updateLegalInfo.handle(cmd);
        LegalInfo legalInfo = result.profile().legalInfo();

        assertThat(legalInfo.legalName()).isEqualTo("Trần Văn Bình");
        assertThat(legalInfo.type()).isEqualTo(SellerType.INDIVIDUAL);
        assertThat(legalInfo.identityNumber().value()).isEqualTo("079123456789");
        assertThat(legalInfo.taxCode()).isNull();
        assertThat(legalInfo.businessAddress().value()).isEqualTo("12 Nguyễn Huệ, Quận 1, TP.HCM");
    }

    @Test
    @DisplayName("Đổi số căn cước thì tính lại khóa định danh")
    void changeIdentityNumberRecomputesKey() {
        UUID owner = UUID.fromString("00000000-0000-0000-0000-000000000002");
        createIndividualProfile(owner, "079123456789", null);

        UpdateLegalInfo.Command cmd = new UpdateLegalInfo.Command(
                user(owner),
                Patch.absent(),
                Patch.absent(),
                Patch.absent(),
                Patch.of("079987654321"),
                Patch.absent(),
                Patch.absent()
        );

        UpdateLegalInfo.Result result = updateLegalInfo.handle(cmd);
        assertThat(result.profile().legalInfo().identityNumber().value()).isEqualTo("079987654321");
        assertThat(result.profile().legalInfo().identityKey()).isEqualTo("079987654321");
    }

    @Test
    @DisplayName("Cá nhân có thể thêm mã số thuế rồi xóa mã số thuế")
    void individualMayAddThenClearTaxCode() {
        UUID owner = UUID.fromString("00000000-0000-0000-0000-000000000003");
        createIndividualProfile(owner, "079123456789", null);

        // Thêm mã số thuế
        UpdateLegalInfo.Command addTaxCmd = new UpdateLegalInfo.Command(
                user(owner),
                Patch.absent(),
                Patch.absent(),
                Patch.absent(),
                Patch.absent(),
                Patch.of("0312345678"),
                Patch.absent()
        );
        UpdateLegalInfo.Result addResult = updateLegalInfo.handle(addTaxCmd);
        assertThat(addResult.profile().legalInfo().taxCode().value()).isEqualTo("0312345678");
        // Khóa định danh của cá nhân vẫn là số căn cước
        assertThat(addResult.profile().legalInfo().identityKey()).isEqualTo("079123456789");

        // Xóa mã số thuế
        UpdateLegalInfo.Command clearTaxCmd = new UpdateLegalInfo.Command(
                user(owner),
                Patch.absent(),
                Patch.absent(),
                Patch.absent(),
                Patch.absent(),
                Patch.of(null),
                Patch.absent()
        );
        UpdateLegalInfo.Result clearResult = updateLegalInfo.handle(clearTaxCmd);
        assertThat(clearResult.profile().legalInfo().taxCode()).isNull();
    }

    @Test
    @DisplayName("Đổi loại hình sang BUSINESS không có đại diện hoặc MST báo lỗi LEGAL_INFO_REQUIRED")
    void changeTypeToBusinessWithoutRepresentativeIsRequiredError() {
        UUID owner = UUID.fromString("00000000-0000-0000-0000-000000000004");
        createIndividualProfile(owner, "079123456789", null);

        // Thiếu cả representativeName và taxCode
        UpdateLegalInfo.Command invalidTypeCmd = new UpdateLegalInfo.Command(
                user(owner),
                Patch.of(SellerType.BUSINESS),
                Patch.absent(),
                Patch.absent(),
                Patch.absent(),
                Patch.absent(),
                Patch.absent()
        );
        assertThatThrownBy(() -> updateLegalInfo.handle(invalidTypeCmd))
                .isInstanceOf(DomainException.class)
                .satisfies(ex -> assertThat(((DomainException) ex).getErrorCode().code())
                        .isEqualTo(SellerErrorCode.LEGAL_INFO_REQUIRED.code()));

        // Đủ representativeName và taxCode thì thành công và khóa định danh = mã số thuế
        UpdateLegalInfo.Command validTypeCmd = new UpdateLegalInfo.Command(
                user(owner),
                Patch.of(SellerType.BUSINESS),
                Patch.of("Công ty TNHH Sao Mai"),
                Patch.of("Lê Quang Cường"),
                Patch.absent(),
                Patch.of("0312345678"),
                Patch.absent()
        );
        UpdateLegalInfo.Result result = updateLegalInfo.handle(validTypeCmd);
        LegalInfo legalInfo = result.profile().legalInfo();
        assertThat(legalInfo.type()).isEqualTo(SellerType.BUSINESS);
        assertThat(legalInfo.representativeName()).isEqualTo("Lê Quang Cường");
        assertThat(legalInfo.taxCode().value()).isEqualTo("0312345678");
        assertThat(legalInfo.identityKey()).isEqualTo("0312345678");
    }

    @Test
    @DisplayName("Đổi sang khóa định danh đang là khóa hiệu lực của người khác thì bị từ chối")
    void identityTakenByOtherRejected() {
        UUID ownerA = UUID.fromString("00000000-0000-0000-0000-00000000000A");
        UUID ownerB = UUID.fromString("00000000-0000-0000-0000-00000000000B");
        createIndividualProfile(ownerA, "079111111111", null);
        createIndividualProfile(ownerB, "079222222222", null);

        // B đổi sang số căn cước của A
        UpdateLegalInfo.Command cmdConflict = new UpdateLegalInfo.Command(
                user(ownerB),
                Patch.absent(),
                Patch.absent(),
                Patch.absent(),
                Patch.of("079111111111"),
                Patch.absent(),
                Patch.absent()
        );
        assertThatThrownBy(() -> updateLegalInfo.handle(cmdConflict))
                .isInstanceOf(DomainException.class)
                .satisfies(ex -> assertThat(((DomainException) ex).getErrorCode().code())
                        .isEqualTo(SellerErrorCode.IDENTITY_ALREADY_REGISTERED.code()));

        // Khóa của hồ sơ REJECTED của người khác thì được
        UUID ownerRejected = UUID.fromString("00000000-0000-0000-0000-00000000000C");
        fakeRepository.save(rejectedProfile(ownerRejected, "079333333333", "Shop C"));
        UpdateLegalInfo.Command cmdOkWithRejectedKey = new UpdateLegalInfo.Command(
                user(ownerB),
                Patch.absent(),
                Patch.absent(),
                Patch.absent(),
                Patch.of("079333333333"),
                Patch.absent(),
                Patch.absent()
        );
        UpdateLegalInfo.Result resultOk = updateLegalInfo.handle(cmdOkWithRejectedKey);
        assertThat(resultOk.profile().legalInfo().identityNumber().value()).isEqualTo("079333333333");
    }

    @Test
    @DisplayName("Hồ sơ APPROVED bị khóa thông tin pháp lý, hồ sơ REJECTED báo INVALID_STATE")
    void approvedProfileLegalInfoLocked() {
        UUID ownerApproved = UUID.fromString("00000000-0000-0000-0000-000000000001");
        fakeRepository.save(approvedProfile(ownerApproved, "079111111111", "Shop Approved"));

        UpdateLegalInfo.Command cmdApproved = new UpdateLegalInfo.Command(
                user(ownerApproved),
                Patch.absent(),
                Patch.of("Tên Pháp Lý Mới"),
                Patch.absent(),
                Patch.absent(),
                Patch.absent(),
                Patch.absent()
        );
        assertThatThrownBy(() -> updateLegalInfo.handle(cmdApproved))
                .isInstanceOf(DomainException.class)
                .satisfies(ex -> assertThat(((DomainException) ex).getErrorCode().code())
                        .isEqualTo(SellerErrorCode.LEGAL_INFO_LOCKED.code()));

        UUID ownerRejected = UUID.fromString("00000000-0000-0000-0000-000000000002");
        fakeRepository.save(rejectedProfile(ownerRejected, "079222222222", "Shop Rejected"));

        UpdateLegalInfo.Command cmdRejected = new UpdateLegalInfo.Command(
                user(ownerRejected),
                Patch.absent(),
                Patch.of("Tên Pháp Lý Mới"),
                Patch.absent(),
                Patch.absent(),
                Patch.absent(),
                Patch.absent()
        );
        assertThatThrownBy(() -> updateLegalInfo.handle(cmdRejected))
                .isInstanceOf(DomainException.class)
                .satisfies(ex -> assertThat(((DomainException) ex).getErrorCode().code())
                        .isEqualTo(SellerErrorCode.INVALID_STATE.code()));
    }

    @Test
    @DisplayName("Không thay đổi trả về hồ sơ hiện tại không lưu, có thay đổi lưu đúng một lần")
    void noChangeReturnsCurrentWithoutSavingAndChangeSavesOnce() {
        UUID owner = UUID.fromString("00000000-0000-0000-0000-000000000005");
        SellerProfile initial = createIndividualProfile(owner, "079123456789", null);
        Instant originalUpdatedAt = initial.updatedAt();

        // Không thay đổi
        UpdateLegalInfo.Command cmdNoChange = new UpdateLegalInfo.Command(
                user(owner),
                Patch.absent(),
                Patch.absent(),
                Patch.absent(),
                Patch.absent(),
                Patch.absent(),
                Patch.absent()
        );
        UpdateLegalInfo.Result result1 = updateLegalInfo.handle(cmdNoChange);
        assertThat(result1.profile().updatedAt()).isEqualTo(originalUpdatedAt);
        assertThat(countingRepository.getSaveCount()).isZero();

        // Có thay đổi
        UpdateLegalInfo.Command cmdChange = new UpdateLegalInfo.Command(
                user(owner),
                Patch.absent(),
                Patch.of("Nguyễn Văn Bình"),
                Patch.absent(),
                Patch.absent(),
                Patch.absent(),
                Patch.absent()
        );
        UpdateLegalInfo.Result result2 = updateLegalInfo.handle(cmdChange);
        assertThat(countingRepository.getSaveCount()).isEqualTo(1);
        assertThat(result2.profile().updatedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("Chưa có hồ sơ thì SELLER_PROFILE_NOT_FOUND, ADMIN bị từ chối SELLER_ROLE_NOT_ALLOWED")
    void noProfileIsNotFound() {
        UUID nonExistent = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UpdateLegalInfo.Command cmdNotFound = new UpdateLegalInfo.Command(
                user(nonExistent),
                Patch.absent(),
                Patch.of("Name"),
                Patch.absent(),
                Patch.absent(),
                Patch.absent(),
                Patch.absent()
        );
        assertThatThrownBy(() -> updateLegalInfo.handle(cmdNotFound))
                .isInstanceOf(DomainException.class)
                .satisfies(ex -> assertThat(((DomainException) ex).getErrorCode().code())
                        .isEqualTo(SellerErrorCode.SELLER_PROFILE_NOT_FOUND.code()));

        UpdateLegalInfo.Command cmdAdmin = new UpdateLegalInfo.Command(
                admin(nonExistent),
                Patch.absent(),
                Patch.of("Name"),
                Patch.absent(),
                Patch.absent(),
                Patch.absent(),
                Patch.absent()
        );
        assertThatThrownBy(() -> updateLegalInfo.handle(cmdAdmin))
                .isInstanceOf(DomainException.class)
                .satisfies(ex -> assertThat(((DomainException) ex).getErrorCode().code())
                        .isEqualTo(SellerErrorCode.SELLER_ROLE_NOT_ALLOWED.code()));
    }

    @Test
    @DisplayName("Thông báo ngoại lệ không bao giờ chứa giá trị nhạy cảm đã nộp")
    void errorMessagesNeverContainSubmittedValues() {
        UUID owner = UUID.fromString("00000000-0000-0000-0000-000000000001");
        createIndividualProfile(owner, "079123456789", null);

        String invalidIdentity = "12ab";
        UpdateLegalInfo.Command cmd = new UpdateLegalInfo.Command(
                user(owner),
                Patch.absent(),
                Patch.absent(),
                Patch.absent(),
                Patch.of(invalidIdentity),
                Patch.absent(),
                Patch.absent()
        );

        assertThatThrownBy(() -> updateLegalInfo.handle(cmd))
                .isInstanceOf(DomainException.class)
                .satisfies(ex -> assertThat(ex.getMessage()).doesNotContain(invalidIdentity));
    }

    /**
     * Kho bọc ủy quyền cho FakeSellerProfileRepository để đếm số lần gọi save.
     */
    private static class CountingSellerProfileRepository implements SellerProfileRepository {
        private final FakeSellerProfileRepository delegate;
        private final AtomicInteger saveCount = new AtomicInteger(0);

        CountingSellerProfileRepository(FakeSellerProfileRepository delegate) {
            this.delegate = delegate;
        }

        void resetSaveCount() {
            saveCount.set(0);
        }

        int getSaveCount() {
            return saveCount.get();
        }

        @Override
        public void save(SellerProfile profile) {
            saveCount.incrementAndGet();
            delegate.save(profile);
        }

        @Override
        public Optional<SellerProfile> findById(SellerProfileId id) {
            return delegate.findById(id);
        }

        @Override
        public Optional<SellerProfile> findByIdForUpdate(SellerProfileId id) {
            return delegate.findByIdForUpdate(id);
        }

        @Override
        public Optional<SellerProfile> findByOwner(UUID ownerAccountId) {
            return delegate.findByOwner(ownerAccountId);
        }

        @Override
        public boolean existsByOwner(UUID ownerAccountId) {
            return delegate.existsByOwner(ownerAccountId);
        }

        @Override
        public boolean existsShopNameKeyUsedByOther(String shopNameKey, UUID ownerAccountId) {
            return delegate.existsShopNameKeyUsedByOther(shopNameKey, ownerAccountId);
        }

        @Override
        public boolean existsEffectiveIdentityKeyUsedByOther(String identityKey, UUID ownerAccountId) {
            return delegate.existsEffectiveIdentityKeyUsedByOther(identityKey, ownerAccountId);
        }
    }
}
