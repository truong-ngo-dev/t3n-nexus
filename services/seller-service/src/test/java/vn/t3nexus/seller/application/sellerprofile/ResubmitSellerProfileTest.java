package vn.t3nexus.seller.application.sellerprofile;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import vn.t3nexus.lib.common.domain.exception.DomainException;
import vn.t3nexus.lib.common.domain.model.DomainEvent;
import vn.t3nexus.lib.common.application.EventDispatcher;
import vn.t3nexus.lib.common.domain.service.EventHandler;
import vn.t3nexus.lib.outbox.OutboxEventStore;
import vn.t3nexus.seller.application.sellerprofile.event.SellerProfileSubmittedHandler;
import vn.t3nexus.seller.domain.sellerprofile.FakeSellerProfileRepository;
import vn.t3nexus.seller.domain.sellerprofile.SellerActor;
import vn.t3nexus.seller.domain.sellerprofile.SellerErrorCode;
import vn.t3nexus.seller.domain.sellerprofile.SellerProfile;
import vn.t3nexus.seller.domain.sellerprofile.SellerProfileStatus;
import vn.t3nexus.seller.domain.sellerprofile.SellerProfileSubmitted;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static vn.t3nexus.seller.application.sellerprofile.UseCaseTestSupport.NOW;
import static vn.t3nexus.seller.application.sellerprofile.UseCaseTestSupport.admin;
import static vn.t3nexus.seller.application.sellerprofile.UseCaseTestSupport.approvedProfile;
import static vn.t3nexus.seller.application.sellerprofile.UseCaseTestSupport.clock;
import static vn.t3nexus.seller.application.sellerprofile.UseCaseTestSupport.ids;
import static vn.t3nexus.seller.application.sellerprofile.UseCaseTestSupport.rejectedProfile;
import static vn.t3nexus.seller.application.sellerprofile.UseCaseTestSupport.resubmitCommand;
import static vn.t3nexus.seller.application.sellerprofile.UseCaseTestSupport.submitCommand;
import static vn.t3nexus.seller.application.sellerprofile.UseCaseTestSupport.user;

/**
 * Kiểm thử use case ResubmitSellerProfile.
 */
class ResubmitSellerProfileTest {

    private FakeSellerProfileRepository repository;
    private OutboxEventStore outbox;
    private EventDispatcher eventDispatcher;
    private ResubmitSellerProfile resubmitSellerProfile;

    @BeforeEach
    void setUp() {
        repository = new FakeSellerProfileRepository();
        outbox = mock(OutboxEventStore.class);
        eventDispatcher = new EventDispatcher(List.<EventHandler<?>>of(new SellerProfileSubmittedHandler(outbox)));
        resubmitSellerProfile = new ResubmitSellerProfile(repository, ids(), clock(), eventDispatcher);
    }

    @Test
    @DisplayName("Nộp lại chuyển hồ sơ REJECTED sang PENDING_REVIEW, xóa lý do từ chối và ghi đúng một sự kiện outbox")
    void resubmitMovesRejectedToPendingClearsReasonAndWritesEvent() {
        UUID ownerA = UUID.fromString("00000000-0000-0000-0000-00000000000A");
        String k = "079123456789";
        repository.save(rejectedProfile(ownerA, k, "Tên Cũ Của A"));

        ResubmitSellerProfile.Command command = resubmitCommand(user(ownerA), k, "Tên Mới Của A");
        ResubmitSellerProfile.Result result = resubmitSellerProfile.handle(command);

        SellerProfile profile = result.profile();
        assertThat(profile.status()).isEqualTo(SellerProfileStatus.PENDING_REVIEW);
        assertThat(profile.rejectionReason()).isNull();
        assertThat(profile.submittedAt()).isEqualTo(NOW);
        assertThat(profile.shopInfo().shopName().display()).isEqualTo("Tên Mới Của A");

        // Hộp thư nhận đúng 1 sự kiện SellerProfileSubmitted với submittedAt = NOW
        ArgumentCaptor<DomainEvent> captor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(outbox, times(1)).store(captor.capture());

        DomainEvent event = captor.getValue();
        assertThat(event).isInstanceOf(SellerProfileSubmitted.class);
        SellerProfileSubmitted submittedEvent = (SellerProfileSubmitted) event;
        assertThat(submittedEvent.submittedAt()).isEqualTo(NOW);
        assertThat(submittedEvent.accountId()).isEqualTo(ownerA);
        assertThat(submittedEvent.profileId()).isEqualTo(profile.getId());
    }

    @Test
    @DisplayName("Nộp lại khi chưa có hồ sơ thì báo lỗi SELLER_PROFILE_NOT_FOUND và không ghi outbox")
    void resubmitWithoutProfileIsNotFound() {
        UUID owner = UUID.fromString("00000000-0000-0000-0000-000000000001");
        ResubmitSellerProfile.Command command = resubmitCommand(user(owner), "079123456789", "Shop Name");

        assertThatThrownBy(() -> resubmitSellerProfile.handle(command))
                .isInstanceOf(DomainException.class)
                .satisfies(ex -> assertThat(((DomainException) ex).getErrorCode().code())
                        .isEqualTo(SellerErrorCode.SELLER_PROFILE_NOT_FOUND.code()));

        verifyNoInteractions(outbox);
    }

    @Test
    @DisplayName("Nộp lại khi hồ sơ đang PENDING_REVIEW hoặc APPROVED thì báo lỗi INVALID_STATE")
    void resubmitWhenPendingOrApprovedIsInvalidState() {
        UUID ownerPending = UUID.fromString("00000000-0000-0000-0000-000000000001");
        SubmitSellerProfile submitUseCase = new SubmitSellerProfile(repository, ids(), clock(), eventDispatcher);
        submitUseCase.handle(submitCommand(user(ownerPending), "079111111111", "Shop Pending"));

        // Nộp lại hồ sơ PENDING_REVIEW
        ResubmitSellerProfile.Command resubmitPendingCmd = resubmitCommand(user(ownerPending), "079111111111", "Shop Pending New");
        assertThatThrownBy(() -> resubmitSellerProfile.handle(resubmitPendingCmd))
                .isInstanceOf(DomainException.class)
                .satisfies(ex -> assertThat(((DomainException) ex).getErrorCode().code())
                        .isEqualTo(SellerErrorCode.INVALID_STATE.code()));

        // Nộp lại hồ sơ APPROVED
        UUID ownerApproved = UUID.fromString("00000000-0000-0000-0000-000000000002");
        repository.save(approvedProfile(ownerApproved, "079222222222", "Shop Approved"));
        ResubmitSellerProfile.Command resubmitApprovedCmd = resubmitCommand(user(ownerApproved), "079222222222", "Shop Approved New");
        assertThatThrownBy(() -> resubmitSellerProfile.handle(resubmitApprovedCmd))
                .isInstanceOf(DomainException.class)
                .satisfies(ex -> assertThat(((DomainException) ex).getErrorCode().code())
                        .isEqualTo(SellerErrorCode.INVALID_STATE.code()));

        // verify outbox chỉ có 1 lần duy nhất từ lệnh submit ban đầu của ownerPending, không thêm lần nào từ các lần resubmit
        verify(outbox, times(1)).store(ArgumentCaptor.forClass(DomainEvent.class).capture());
    }

    @Test
    @DisplayName("Nộp lại kiểm tra lại định danh nếu đã bị người khác đăng ký trong thời gian đó (E15)")
    void resubmitRechecksIdentityKeyTakenMeanwhile() {
        UUID ownerA = UUID.fromString("00000000-0000-0000-0000-00000000000A");
        UUID ownerB = UUID.fromString("00000000-0000-0000-0000-00000000000B");
        String k = "079111111111";
        String k2 = "079222222222";

        repository.save(rejectedProfile(ownerA, k, "Shop A"));

        // B nộp thành công với khóa K2
        SubmitSellerProfile submitUseCase = new SubmitSellerProfile(repository, ids(), clock(), eventDispatcher);
        submitUseCase.handle(submitCommand(user(ownerB), k2, "Shop B"));

        // A nộp lại với khóa K2
        ResubmitSellerProfile.Command resubmitCmd = resubmitCommand(user(ownerA), k2, "Shop A New");
        assertThatThrownBy(() -> resubmitSellerProfile.handle(resubmitCmd))
                .isInstanceOf(DomainException.class)
                .satisfies(ex -> assertThat(((DomainException) ex).getErrorCode().code())
                        .isEqualTo(SellerErrorCode.IDENTITY_ALREADY_REGISTERED.code()));

        // Outbox chỉ có 1 lần store từ bước B nộp thành công, A nộp lại không ghi thêm
        verify(outbox, times(1)).store(ArgumentCaptor.forClass(DomainEvent.class).capture());
    }

    @Test
    @DisplayName("Nộp lại kiểm tra lại tên gian hàng nếu đã bị người khác sử dụng")
    void resubmitRechecksShopNameTaken() {
        UUID ownerA = UUID.fromString("00000000-0000-0000-0000-00000000000A");
        UUID ownerB = UUID.fromString("00000000-0000-0000-0000-00000000000B");

        repository.save(rejectedProfile(ownerA, "079111111111", "Shop A Old"));

        SubmitSellerProfile submitUseCase = new SubmitSellerProfile(repository, ids(), clock(), eventDispatcher);
        submitUseCase.handle(submitCommand(user(ownerB), "079222222222", "Shop B"));

        // A nộp lại với tên Shop B
        ResubmitSellerProfile.Command resubmitCmd = resubmitCommand(user(ownerA), "079111111111", "Shop B");
        assertThatThrownBy(() -> resubmitSellerProfile.handle(resubmitCmd))
                .isInstanceOf(DomainException.class)
                .satisfies(ex -> assertThat(((DomainException) ex).getErrorCode().code())
                        .isEqualTo(SellerErrorCode.SHOP_NAME_TAKEN.code()));
    }

    @Test
    @DisplayName("Nộp lại giữ nguyên tên gian hàng và khóa cũ của chính mình thì thành công")
    void resubmitKeepingOwnNameAndKeyIsAllowed() {
        UUID ownerA = UUID.fromString("00000000-0000-0000-0000-00000000000A");
        String k = "079111111111";
        String shopName = "Shop Của A";

        repository.save(rejectedProfile(ownerA, k, shopName));

        ResubmitSellerProfile.Command resubmitCmd = resubmitCommand(user(ownerA), k, shopName);
        ResubmitSellerProfile.Result result = resubmitSellerProfile.handle(resubmitCmd);

        assertThat(result.profile().status()).isEqualTo(SellerProfileStatus.PENDING_REVIEW);
        verify(outbox, times(1)).store(ArgumentCaptor.forClass(DomainEvent.class).capture());
    }

    @Test
    @DisplayName("ADMIN không được phép nộp lại hồ sơ")
    void adminCannotResubmit() {
        UUID owner = UUID.fromString("00000000-0000-0000-0000-000000000001");
        repository.save(rejectedProfile(owner, "079111111111", "Shop"));

        ResubmitSellerProfile.Command adminCmd = resubmitCommand(admin(owner), "079111111111", "Shop New");
        assertThatThrownBy(() -> resubmitSellerProfile.handle(adminCmd))
                .isInstanceOf(DomainException.class)
                .satisfies(ex -> assertThat(((DomainException) ex).getErrorCode().code())
                        .isEqualTo(SellerErrorCode.SELLER_ROLE_NOT_ALLOWED.code()));

        verifyNoInteractions(outbox);
    }
}
