package vn.t3nexus.seller.application.sellerprofile;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import vn.t3nexus.lib.common.domain.exception.DomainException;
import vn.t3nexus.lib.common.domain.model.DomainEvent;
import vn.t3nexus.lib.outbox.OutboxEventStore;
import vn.t3nexus.seller.domain.sellerprofile.FakeSellerProfileRepository;
import vn.t3nexus.seller.domain.sellerprofile.SellerActor;
import vn.t3nexus.seller.domain.sellerprofile.SellerErrorCode;
import vn.t3nexus.seller.domain.sellerprofile.SellerFieldException;
import vn.t3nexus.seller.domain.sellerprofile.SellerProfile;
import vn.t3nexus.seller.domain.sellerprofile.SellerProfileId;
import vn.t3nexus.seller.domain.sellerprofile.SellerProfileStatus;
import vn.t3nexus.seller.domain.sellerprofile.SellerProfileSubmitted;
import vn.t3nexus.seller.domain.sellerprofile.SellerType;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static vn.t3nexus.seller.application.sellerprofile.UseCaseTestSupport.NOW;
import static vn.t3nexus.seller.application.sellerprofile.UseCaseTestSupport.admin;
import static vn.t3nexus.seller.application.sellerprofile.UseCaseTestSupport.clock;
import static vn.t3nexus.seller.application.sellerprofile.UseCaseTestSupport.ids;
import static vn.t3nexus.seller.application.sellerprofile.UseCaseTestSupport.rejectedProfile;
import static vn.t3nexus.seller.application.sellerprofile.UseCaseTestSupport.submitCommand;
import static vn.t3nexus.seller.application.sellerprofile.UseCaseTestSupport.user;

/**
 * Kiểm thử use case SubmitSellerProfile.
 */
class SubmitSellerProfileTest {

    private FakeSellerProfileRepository repository;
    private OutboxEventStore outbox;
    private SubmitSellerProfile submitSellerProfile;

    @BeforeEach
    void setUp() {
        repository = new FakeSellerProfileRepository();
        outbox = mock(OutboxEventStore.class);
        submitSellerProfile = new SubmitSellerProfile(repository, ids(), clock(), outbox);
    }

    @Test
    @DisplayName("Nộp hồ sơ thành công tạo hồ sơ PENDING_REVIEW và ghi đúng một sự kiện outbox")
    void submitCreatesPendingProfileAndWritesOneEvent() {
        UUID owner = UUID.fromString("00000000-0000-0000-0000-000000000001");
        SellerActor actor = user(owner);
        SubmitSellerProfile.Command command = submitCommand(actor, "079123456789", "Gian Hàng Hoa Tươi");

        SubmitSellerProfile.Result result = submitSellerProfile.handle(command);

        SellerProfile profile = result.profile();
        assertThat(profile.status()).isEqualTo(SellerProfileStatus.PENDING_REVIEW);
        assertThat(profile.ownerAccountId()).isEqualTo(owner);
        assertThat(profile.submittedAt()).isEqualTo(NOW);

        // Kho giả lưu đúng hồ sơ đó
        Optional<SellerProfile> saved = repository.findByOwner(owner);
        assertThat(saved).isPresent().contains(profile);

        // Hộp thư nhận đúng 1 lần store
        ArgumentCaptor<DomainEvent> captor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(outbox, times(1)).store(captor.capture());

        DomainEvent event = captor.getValue();
        assertThat(event).isInstanceOf(SellerProfileSubmitted.class);
        SellerProfileSubmitted submittedEvent = (SellerProfileSubmitted) event;
        assertThat(submittedEvent.accountId()).isEqualTo(owner);
        assertThat(submittedEvent.profileId()).isEqualTo(profile.getId());
        assertThat(submittedEvent.getOccurredOn()).isEqualTo(NOW);

        // Mã hồ sơ và mã sự kiện lấy từ bộ sinh mã (hai giá trị khác nhau)
        // ids() đếm từ 1: mã hồ sơ là UUID(0, 1), mã sự kiện là UUID(0, 2)
        assertThat(profile.getId()).isEqualTo(SellerProfileId.of(new UUID(0, 1)));
        assertThat(submittedEvent.getEventId()).isEqualTo(new UUID(0, 2).toString());
        assertThat(profile.getId().getValue()).isNotEqualTo(UUID.fromString(submittedEvent.getEventId()));
    }

    @Test
    @DisplayName("Tài khoản ADMIN bị từ chối trước khi kiểm tra định dạng")
    void adminIsRejectedBeforeAnythingElse() {
        UUID adminId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        SellerActor actor = admin(adminId);
        // Có trường sai định dạng (số căn cước "123")
        SubmitSellerProfile.Command command = submitCommand(actor, "123", "Gian Hàng");

        assertThatThrownBy(() -> submitSellerProfile.handle(command))
                .isInstanceOf(DomainException.class)
                .satisfies(ex -> assertThat(((DomainException) ex).getErrorCode().code())
                        .isEqualTo(SellerErrorCode.SELLER_ROLE_NOT_ALLOWED.code()));

        assertThat(repository.findByOwner(adminId)).isEmpty();
        verifyNoInteractions(outbox);
    }

    @Test
    @DisplayName("Trường dữ liệu sai bị từ chối kèm tên trường và không gây hiệu ứng phụ")
    void invalidFieldRejectedWithFieldNameAndNoSideEffects() {
        UUID owner = UUID.fromString("00000000-0000-0000-0000-000000000003");
        SellerActor actor = user(owner);

        // Số căn cước "123" sai định dạng
        SubmitSellerProfile.Command invalidIdentityCmd = submitCommand(actor, "123", "Gian Hàng");
        assertThatThrownBy(() -> submitSellerProfile.handle(invalidIdentityCmd))
                .isInstanceOf(SellerFieldException.class)
                .satisfies(ex -> {
                    SellerFieldException sfe = (SellerFieldException) ex;
                    assertThat(sfe.getErrorCode().code()).isEqualTo(SellerErrorCode.INVALID_IDENTITY_NUMBER.code());
                    assertThat(sfe.field()).isEqualTo("identityNumber");
                    assertThat(sfe.getMessage()).doesNotContain("123");
                });

        assertThat(repository.findByOwner(owner)).isEmpty();
        verifyNoInteractions(outbox);

        // Tên gian hàng "ab" (quá ngắn < 3 ký tự)
        SubmitSellerProfile.Command invalidShopNameCmd = submitCommand(actor, "079123456789", "ab");
        assertThatThrownBy(() -> submitSellerProfile.handle(invalidShopNameCmd))
                .isInstanceOf(SellerFieldException.class)
                .satisfies(ex -> {
                    SellerFieldException sfe = (SellerFieldException) ex;
                    assertThat(sfe.getErrorCode().code()).isEqualTo(SellerErrorCode.SELLER_FIELD_INVALID.code());
                    assertThat(sfe.field()).isEqualTo("shopName");
                });

        assertThat(repository.findByOwner(owner)).isEmpty();
        verifyNoInteractions(outbox);
    }

    @Test
    @DisplayName("Chủ tài khoản đã có hồ sơ thì không được nộp lại và báo lỗi SELLER_PROFILE_EXISTS")
    void ownerAlreadyHasProfileRejected() {
        UUID owner = UUID.fromString("00000000-0000-0000-0000-000000000004");
        SellerActor actor = user(owner);

        SubmitSellerProfile.Command firstCmd = submitCommand(actor, "079123456789", "Gian Hàng Một");
        submitSellerProfile.handle(firstCmd);

        // Nộp lần hai cùng chủ (tên gian hàng và số căn cước khác)
        SubmitSellerProfile.Command secondCmd = submitCommand(actor, "079987654321", "Gian Hàng Hai");
        assertThatThrownBy(() -> submitSellerProfile.handle(secondCmd))
                .isInstanceOf(DomainException.class)
                .satisfies(ex -> assertThat(((DomainException) ex).getErrorCode().code())
                        .isEqualTo(SellerErrorCode.SELLER_PROFILE_EXISTS.code()));

        // Hộp thư chỉ có đúng 1 lần từ lần đầu
        verify(outbox, times(1)).store(ArgumentCaptor.forClass(DomainEvent.class).capture());
        // Kho vẫn chỉ 1 hồ sơ
        assertThat(repository.findByOwner(owner)).isPresent();
    }

    @Test
    @DisplayName("Tên gian hàng trùng lặp không phân biệt hoa thường dấu cách sẽ bị từ chối")
    void shopNameTakenIgnoringCaseDiacriticsAndSpaces() {
        UUID ownerA = UUID.fromString("00000000-0000-0000-0000-00000000000A");
        UUID ownerB = UUID.fromString("00000000-0000-0000-0000-00000000000B");

        SubmitSellerProfile.Command cmdA = submitCommand(user(ownerA), "079111111111", "Cửa Hàng Hoa");
        submitSellerProfile.handle(cmdA);

        SubmitSellerProfile.Command cmdB = submitCommand(user(ownerB), "079222222222", "  CUA   HANG hoa ");
        assertThatThrownBy(() -> submitSellerProfile.handle(cmdB))
                .isInstanceOf(DomainException.class)
                .satisfies(ex -> {
                    DomainException de = (DomainException) ex;
                    assertThat(de.getErrorCode().code()).isEqualTo(SellerErrorCode.SHOP_NAME_TAKEN.code());
                    assertThat(de.getMessage()).doesNotContain(ownerA.toString());
                });

        verify(outbox, times(1)).store(ArgumentCaptor.forClass(DomainEvent.class).capture());
        assertThat(repository.findByOwner(ownerB)).isEmpty();
    }

    @Test
    @DisplayName("Khóa định danh trùng với hồ sơ đang hiệu lực sẽ bị từ chối")
    void identityKeyTakenByEffectiveProfileRejected() {
        UUID ownerA = UUID.fromString("00000000-0000-0000-0000-00000000000A");
        UUID ownerB = UUID.fromString("00000000-0000-0000-0000-00000000000B");
        String k = "079123456789";

        SubmitSellerProfile.Command cmdA = submitCommand(user(ownerA), k, "Tên Shop A");
        submitSellerProfile.handle(cmdA);

        SubmitSellerProfile.Command cmdB = submitCommand(user(ownerB), k, "Tên Shop B");
        assertThatThrownBy(() -> submitSellerProfile.handle(cmdB))
                .isInstanceOf(DomainException.class)
                .satisfies(ex -> {
                    DomainException de = (DomainException) ex;
                    assertThat(de.getErrorCode().code()).isEqualTo(SellerErrorCode.IDENTITY_ALREADY_REGISTERED.code());
                    assertThat(de.getMessage()).doesNotContain(k);
                });

        verify(outbox, times(1)).store(ArgumentCaptor.forClass(DomainEvent.class).capture());
        assertThat(repository.findByOwner(ownerB)).isEmpty();
    }

    @Test
    @DisplayName("Khóa định danh của hồ sơ bị từ chối REJECTED không chặn người khác nộp")
    void identityKeyOfRejectedProfileDoesNotBlock() {
        UUID ownerA = UUID.fromString("00000000-0000-0000-0000-00000000000A");
        UUID ownerB = UUID.fromString("00000000-0000-0000-0000-00000000000B");
        String k = "079123456789";

        repository.save(rejectedProfile(ownerA, k, "Tên Của A"));

        SubmitSellerProfile.Command cmdB = submitCommand(user(ownerB), k, "Tên Của B");
        SubmitSellerProfile.Result resultB = submitSellerProfile.handle(cmdB);

        assertThat(resultB.profile().ownerAccountId()).isEqualTo(ownerB);
        assertThat(resultB.profile().status()).isEqualTo(SellerProfileStatus.PENDING_REVIEW);
        verify(outbox, times(1)).store(ArgumentCaptor.forClass(DomainEvent.class).capture());
    }

    @Test
    @DisplayName("Hộ kinh doanh và doanh nghiệp trùng mã số thuế bị xung đột định danh")
    void householdAndBusinessWithSameTaxCodeConflict() {
        UUID ownerHousehold = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID ownerBusiness = UUID.fromString("00000000-0000-0000-0000-000000000002");
        String taxCode = "0312345678";

        SubmitSellerProfile.Command householdCmd = new SubmitSellerProfile.Command(
                user(ownerHousehold),
                SellerType.HOUSEHOLD,
                "Trần Thị Bình",
                null,
                "079123456789",
                taxCode,
                "12 Nguyễn Huệ, Quận 1, TP.HCM",
                "Tiệm Bình",
                null,
                "Trần Thị Bình",
                "0912345678",
                null
        );
        submitSellerProfile.handle(householdCmd);

        SubmitSellerProfile.Command businessCmd = new SubmitSellerProfile.Command(
                user(ownerBusiness),
                SellerType.BUSINESS,
                "Công ty TNHH Sao Mai",
                "Lê Quang Cường",
                "079987654321",
                taxCode,
                "12 Nguyễn Huệ, Quận 1, TP.HCM",
                "Công ty Sao Mai Shop",
                null,
                "Lê Quang Cường",
                "0912345678",
                null
        );

        assertThatThrownBy(() -> submitSellerProfile.handle(businessCmd))
                .isInstanceOf(DomainException.class)
                .satisfies(ex -> assertThat(((DomainException) ex).getErrorCode().code())
                        .isEqualTo(SellerErrorCode.IDENTITY_ALREADY_REGISTERED.code()));

        verify(outbox, times(1)).store(ArgumentCaptor.forClass(DomainEvent.class).capture());
        assertThat(repository.findByOwner(ownerBusiness)).isEmpty();
    }
}
