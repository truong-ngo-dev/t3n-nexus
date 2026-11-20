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
import vn.t3nexus.seller.domain.sellerprofile.SellerFieldException;
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
 * Kiểm thử use case UpdateShopInfo.
 */
class UpdateShopInfoTest {

    private FakeSellerProfileRepository fakeRepository;
    private CountingSellerProfileRepository countingRepository;
    private UpdateShopInfo updateShopInfo;

    @BeforeEach
    void setUp() {
        fakeRepository = new FakeSellerProfileRepository();
        countingRepository = new CountingSellerProfileRepository(fakeRepository);
        updateShopInfo = new UpdateShopInfo(countingRepository, clock());
    }

    private SellerProfile createAndSaveProfile(UUID owner, String shopName, String description, String contactName, String phone, String address) {
        LegalInfo legalInfo = LegalInfo.of(
                SellerType.INDIVIDUAL,
                "Nguyễn Văn An",
                null,
                "079123456789",
                null,
                "12 Nguyễn Huệ, Quận 1, TP.HCM"
        );
        ShopInfo shopInfo = ShopInfo.of(
                shopName,
                description,
                Contact.of(contactName, phone, address)
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
    @DisplayName("Các trường absent giữ nguyên giá trị hiện tại khi chỉ đổi shopName")
    void absentFieldsKeepCurrentValues() {
        UUID owner = UUID.fromString("00000000-0000-0000-0000-000000000001");
        createAndSaveProfile(owner, "Tên Cũ", "Mô tả ban đầu", "Liên Hệ Cũ", "0912345678", "Địa chỉ liên hệ cũ");

        UpdateShopInfo.Command cmd = new UpdateShopInfo.Command(
                user(owner),
                Patch.of("Tên Mới"),
                Patch.absent(),
                Patch.absent(),
                Patch.absent(),
                Patch.absent()
        );

        UpdateShopInfo.Result result = updateShopInfo.handle(cmd);
        ShopInfo shopInfo = result.profile().shopInfo();

        assertThat(shopInfo.shopName().display()).isEqualTo("Tên Mới");
        assertThat(shopInfo.description()).isEqualTo("Mô tả ban đầu");
        assertThat(shopInfo.contact().personName()).isEqualTo("Liên Hệ Cũ");
        assertThat(shopInfo.contact().phone().value()).isEqualTo("0912345678");
        assertThat(shopInfo.contact().address().value()).isEqualTo("Địa chỉ liên hệ cũ");
    }

    @Test
    @DisplayName("Gửi Patch.of(null) xóa các trường tùy chọn")
    void explicitNullClearsOptionalFields() {
        UUID owner = UUID.fromString("00000000-0000-0000-0000-000000000002");
        createAndSaveProfile(owner, "Tên Shop", "Mô tả cần xóa", "Liên Hệ", "0912345678", "Địa chỉ cần xóa");

        UpdateShopInfo.Command cmd = new UpdateShopInfo.Command(
                user(owner),
                Patch.absent(),
                Patch.of(null),
                Patch.absent(),
                Patch.absent(),
                Patch.of(null)
        );

        UpdateShopInfo.Result result = updateShopInfo.handle(cmd);
        ShopInfo shopInfo = result.profile().shopInfo();

        assertThat(shopInfo.description()).isNull();
        assertThat(shopInfo.contact().address()).isNull();
        assertThat(shopInfo.shopName().display()).isEqualTo("Tên Shop");
    }

    @Test
    @DisplayName("Hai lần sửa các trường khác nhau đều được giữ lại trên hồ sơ")
    void twoEditsOfDifferentFieldsAreBothKept() {
        UUID owner = UUID.fromString("00000000-0000-0000-0000-000000000003");
        createAndSaveProfile(owner, "Tên Shop", "Mô tả cũ", "Liên Hệ", "0912345678", null);

        // Lần 1 đổi description
        UpdateShopInfo.Command cmd1 = new UpdateShopInfo.Command(
                user(owner),
                Patch.absent(),
                Patch.of("Mô tả mới tinh"),
                Patch.absent(),
                Patch.absent(),
                Patch.absent()
        );
        updateShopInfo.handle(cmd1);

        // Lần 2 đổi contactPhone
        UpdateShopInfo.Command cmd2 = new UpdateShopInfo.Command(
                user(owner),
                Patch.absent(),
                Patch.absent(),
                Patch.absent(),
                Patch.of("0987654321"),
                Patch.absent()
        );
        UpdateShopInfo.Result result = updateShopInfo.handle(cmd2);

        ShopInfo shopInfo = result.profile().shopInfo();
        assertThat(shopInfo.description()).isEqualTo("Mô tả mới tinh");
        assertThat(shopInfo.contact().phone().value()).isEqualTo("0987654321");
    }

    @Test
    @DisplayName("Gửi null cho trường bắt buộc ném SellerFieldException")
    void nullOnRequiredFieldIsFieldError() {
        UUID owner = UUID.fromString("00000000-0000-0000-0000-000000000004");
        createAndSaveProfile(owner, "Tên Shop", "Mô tả", "Liên Hệ", "0912345678", null);

        UpdateShopInfo.Command cmd = new UpdateShopInfo.Command(
                user(owner),
                Patch.of(null),
                Patch.absent(),
                Patch.absent(),
                Patch.absent(),
                Patch.absent()
        );

        assertThatThrownBy(() -> updateShopInfo.handle(cmd))
                .isInstanceOf(SellerFieldException.class)
                .satisfies(ex -> {
                    SellerFieldException sfe = (SellerFieldException) ex;
                    assertThat(sfe.getErrorCode().code()).isEqualTo(SellerErrorCode.SELLER_FIELD_INVALID.code());
                    assertThat(sfe.field()).isEqualTo("shopName");
                });
    }

    @Test
    @DisplayName("Không thay đổi giá trị trả về hồ sơ hiện tại mà không lưu")
    void noChangeReturnsCurrentWithoutSaving() {
        UUID owner = UUID.fromString("00000000-0000-0000-0000-000000000005");
        SellerProfile initial = createAndSaveProfile(owner, "Tên Cố Định", "Mô tả", "Liên Hệ", "0912345678", null);
        Instant originalUpdatedAt = initial.updatedAt();

        // Gửi lệnh mà mọi Patch đều absent
        UpdateShopInfo.Command cmdAllAbsent = new UpdateShopInfo.Command(
                user(owner),
                Patch.absent(),
                Patch.absent(),
                Patch.absent(),
                Patch.absent(),
                Patch.absent()
        );
        UpdateShopInfo.Result result1 = updateShopInfo.handle(cmdAllAbsent);
        assertThat(result1.profile().updatedAt()).isEqualTo(originalUpdatedAt);
        assertThat(countingRepository.getSaveCount()).isZero();

        // Gửi lệnh có giá trị đúng bằng giá trị hiện tại
        UpdateShopInfo.Command cmdSameValues = new UpdateShopInfo.Command(
                user(owner),
                Patch.of("Tên Cố Định"),
                Patch.of("Mô tả"),
                Patch.of("Liên Hệ"),
                Patch.of("0912345678"),
                Patch.absent()
        );
        UpdateShopInfo.Result result2 = updateShopInfo.handle(cmdSameValues);
        assertThat(result2.profile().updatedAt()).isEqualTo(originalUpdatedAt);
        assertThat(countingRepository.getSaveCount()).isZero();
    }

    @Test
    @DisplayName("Có thay đổi thì lưu đúng một lần và cập nhật updatedAt thành NOW")
    void changeSavesOnce() {
        UUID owner = UUID.fromString("00000000-0000-0000-0000-000000000006");
        createAndSaveProfile(owner, "Tên Ban Đầu", "Mô tả", "Liên Hệ", "0912345678", null);

        UpdateShopInfo.Command cmd = new UpdateShopInfo.Command(
                user(owner),
                Patch.of("Tên Hoàn Toàn Mới"),
                Patch.absent(),
                Patch.absent(),
                Patch.absent(),
                Patch.absent()
        );

        UpdateShopInfo.Result result = updateShopInfo.handle(cmd);
        assertThat(countingRepository.getSaveCount()).isEqualTo(1);
        assertThat(result.profile().updatedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("Tên gian hàng đã bị người khác dùng thì từ chối, đổi sang biến thể hoa thường của chính mình thì được")
    void shopNameTakenByOtherRejected() {
        UUID ownerA = UUID.fromString("00000000-0000-0000-0000-00000000000A");
        UUID ownerB = UUID.fromString("00000000-0000-0000-0000-00000000000B");
        createAndSaveProfile(ownerA, "Cửa Hàng Hoa", "Mô tả", "Liên Hệ", "0912345678", null);
        createAndSaveProfile(ownerB, "Tiệm Sách Vui", "Mô tả", "Liên Hệ", "0987654321", null);

        // A đổi sang "CUA HANG HOA" của chính mình thì thành công
        UpdateShopInfo.Command cmdA = new UpdateShopInfo.Command(
                user(ownerA),
                Patch.of("CUA HANG HOA"),
                Patch.absent(),
                Patch.absent(),
                Patch.absent(),
                Patch.absent()
        );
        UpdateShopInfo.Result resultA = updateShopInfo.handle(cmdA);
        assertThat(resultA.profile().shopInfo().shopName().display()).isEqualTo("CUA HANG HOA");

        // Sau đó B cố lấy đúng tên của A (khác dấu, hoa thường, thừa khoảng trắng) thì bị chặn
        UpdateShopInfo.Command cmdB = new UpdateShopInfo.Command(
                user(ownerB),
                Patch.of("  CUA   HANG HOA  "),
                Patch.absent(),
                Patch.absent(),
                Patch.absent(),
                Patch.absent()
        );
        assertThatThrownBy(() -> updateShopInfo.handle(cmdB))
                .isInstanceOf(DomainException.class)
                .satisfies(ex -> assertThat(((DomainException) ex).getErrorCode().code())
                        .isEqualTo(SellerErrorCode.SHOP_NAME_TAKEN.code()));

    }

    @Test
    @DisplayName("Hồ sơ REJECTED không được sửa ShopInfo, hồ sơ APPROVED sửa thành công")
    void rejectedProfileCannotEditShopInfo() {
        UUID ownerRejected = UUID.fromString("00000000-0000-0000-0000-000000000001");
        fakeRepository.save(rejectedProfile(ownerRejected, "079111111111", "Shop Rejected"));

        UpdateShopInfo.Command cmdRejected = new UpdateShopInfo.Command(
                user(ownerRejected),
                Patch.of("Shop Rejected New"),
                Patch.absent(),
                Patch.absent(),
                Patch.absent(),
                Patch.absent()
        );
        assertThatThrownBy(() -> updateShopInfo.handle(cmdRejected))
                .isInstanceOf(DomainException.class)
                .satisfies(ex -> assertThat(((DomainException) ex).getErrorCode().code())
                        .isEqualTo(SellerErrorCode.INVALID_STATE.code()));

        UUID ownerApproved = UUID.fromString("00000000-0000-0000-0000-000000000002");
        fakeRepository.save(approvedProfile(ownerApproved, "079222222222", "Shop Approved"));
        UpdateShopInfo.Command cmdApproved = new UpdateShopInfo.Command(
                user(ownerApproved),
                Patch.of("Shop Approved New"),
                Patch.absent(),
                Patch.absent(),
                Patch.absent(),
                Patch.absent()
        );
        UpdateShopInfo.Result resultApproved = updateShopInfo.handle(cmdApproved);
        assertThat(resultApproved.profile().shopInfo().shopName().display()).isEqualTo("Shop Approved New");
    }

    @Test
    @DisplayName("Chưa có hồ sơ thì SELLER_PROFILE_NOT_FOUND, ADMIN bị từ chối SELLER_ROLE_NOT_ALLOWED")
    void noProfileIsNotFound() {
        UUID nonExistent = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UpdateShopInfo.Command cmdNotFound = new UpdateShopInfo.Command(
                user(nonExistent),
                Patch.of("New Name"),
                Patch.absent(),
                Patch.absent(),
                Patch.absent(),
                Patch.absent()
        );
        assertThatThrownBy(() -> updateShopInfo.handle(cmdNotFound))
                .isInstanceOf(DomainException.class)
                .satisfies(ex -> assertThat(((DomainException) ex).getErrorCode().code())
                        .isEqualTo(SellerErrorCode.SELLER_PROFILE_NOT_FOUND.code()));

        UpdateShopInfo.Command cmdAdmin = new UpdateShopInfo.Command(
                admin(nonExistent),
                Patch.of("New Name"),
                Patch.absent(),
                Patch.absent(),
                Patch.absent(),
                Patch.absent()
        );
        assertThatThrownBy(() -> updateShopInfo.handle(cmdAdmin))
                .isInstanceOf(DomainException.class)
                .satisfies(ex -> assertThat(((DomainException) ex).getErrorCode().code())
                        .isEqualTo(SellerErrorCode.SELLER_ROLE_NOT_ALLOWED.code()));
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
