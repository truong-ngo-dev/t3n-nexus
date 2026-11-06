package vn.t3nexus.seller.application.sellerprofile;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import vn.t3nexus.lib.audit.AuditActor;
import vn.t3nexus.lib.audit.AuditRecorder;
import vn.t3nexus.lib.audit.AuditTarget;
import vn.t3nexus.lib.common.application.EventDispatcher;
import vn.t3nexus.lib.common.domain.exception.DomainException;
import vn.t3nexus.lib.common.domain.service.EventHandler;
import vn.t3nexus.lib.outbox.OutboxEventStore;
import vn.t3nexus.seller.application.sellerprofile.event.SellerProfileRejectedAuditHandler;
import vn.t3nexus.seller.application.sellerprofile.event.SellerProfileRejectedHandler;
import vn.t3nexus.seller.domain.sellerprofile.Contact;
import vn.t3nexus.seller.domain.sellerprofile.FakeSellerProfileRepository;
import vn.t3nexus.seller.domain.sellerprofile.LegalInfo;
import vn.t3nexus.seller.domain.sellerprofile.SellerActor;
import vn.t3nexus.seller.domain.sellerprofile.SellerErrorCode;
import vn.t3nexus.seller.domain.sellerprofile.SellerFieldException;
import vn.t3nexus.seller.domain.sellerprofile.SellerProfile;
import vn.t3nexus.seller.domain.sellerprofile.SellerProfileId;
import vn.t3nexus.seller.domain.sellerprofile.SellerProfileRejected;
import vn.t3nexus.seller.domain.sellerprofile.SellerProfileRepository;
import vn.t3nexus.seller.domain.sellerprofile.SellerProfileStatus;
import vn.t3nexus.seller.domain.sellerprofile.SellerType;
import vn.t3nexus.seller.domain.sellerprofile.ShopInfo;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static vn.t3nexus.seller.application.sellerprofile.UseCaseTestSupport.NOW;
import static vn.t3nexus.seller.application.sellerprofile.UseCaseTestSupport.admin;
import static vn.t3nexus.seller.application.sellerprofile.UseCaseTestSupport.clock;
import static vn.t3nexus.seller.application.sellerprofile.UseCaseTestSupport.ids;
import static vn.t3nexus.seller.application.sellerprofile.UseCaseTestSupport.user;

/**
 * Kiểm thử đơn vị use case RejectSellerProfile.
 */
class RejectSellerProfileTest {

    private FakeSellerProfileRepository repository;
    private OutboxEventStore outbox;
    private AuditRecorder recorder;
    private EventDispatcher eventDispatcher;
    private RejectSellerProfile rejectSellerProfile;

    private static SellerProfile createPendingProfile(UUID profileId, UUID owner, long version) {
        LegalInfo legalInfo = LegalInfo.of(
                SellerType.INDIVIDUAL,
                "Nguyễn Văn An",
                null,
                "079123456789",
                null,
                "12 Nguyễn Huệ, Quận 1, TP.HCM"
        );
        ShopInfo shopInfo = ShopInfo.of(
                "Gian Hàng Hoa Tươi",
                "Mô tả gian hàng",
                Contact.of("Nguyễn Văn An", "0912345678", null)
        );
        return SellerProfile.restore(
                SellerProfileId.of(profileId),
                owner,
                SellerProfileStatus.PENDING_REVIEW,
                legalInfo,
                shopInfo,
                null,
                NOW,
                null,
                null,
                NOW,
                NOW,
                version
        );
    }

    private static SellerProfile createApprovedProfile(UUID profileId, UUID owner, long version) {
        LegalInfo legalInfo = LegalInfo.of(
                SellerType.INDIVIDUAL,
                "Nguyễn Văn An",
                null,
                "079123456789",
                null,
                "12 Nguyễn Huệ, Quận 1, TP.HCM"
        );
        ShopInfo shopInfo = ShopInfo.of(
                "Gian Hàng Hoa Tươi",
                "Mô tả gian hàng",
                Contact.of("Nguyễn Văn An", "0912345678", null)
        );
        return SellerProfile.restore(
                SellerProfileId.of(profileId),
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
                version
        );
    }

    @BeforeEach
    void setUp() {
        repository = new FakeSellerProfileRepository();
        outbox = mock(OutboxEventStore.class);
        recorder = mock(AuditRecorder.class);
        eventDispatcher = new EventDispatcher(List.<EventHandler<?>>of(
                new SellerProfileRejectedHandler(outbox),
                new SellerProfileRejectedAuditHandler(recorder)
        ));
        rejectSellerProfile = new RejectSellerProfile(repository, ids(), clock(), eventDispatcher);
    }

    @Test
    @DisplayName("Từ chối hồ sơ thành công với lý do Giấy tờ mờ")
    void rejectSuccess() {
        UUID profileId = UUID.fromString("00000000-0000-0000-0000-000000000010");
        UUID ownerId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID adminId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        SellerProfile profile = createPendingProfile(profileId, ownerId, 3L);
        repository.save(profile);

        RejectSellerProfile.Command command = new RejectSellerProfile.Command(
                admin(adminId), profileId, 3L, "Giấy tờ mờ"
        );
        RejectSellerProfile.Result result = rejectSellerProfile.handle(command);

        // Result.profile() có trạng thái REJECTED, reviewedBy, rejectionReason
        assertThat(result.profile().status()).isEqualTo(SellerProfileStatus.REJECTED);
        assertThat(result.profile().reviewedBy()).isEqualTo(adminId);
        assertThat(result.profile().rejectionReason()).isEqualTo("Giấy tờ mờ");

        // Kho giả lưu hồ sơ REJECTED
        SellerProfile inRepo = repository.findById(SellerProfileId.of(profileId)).orElseThrow();
        assertThat(inRepo.status()).isEqualTo(SellerProfileStatus.REJECTED);
        assertThat(inRepo.rejectionReason()).isEqualTo("Giấy tờ mờ");

        // profile.getDomainEvents() rỗng sau xử lý
        assertThat(result.profile().getDomainEvents()).isEmpty();

        // verify(outbox).store với SellerProfileRejected version 4L
        ArgumentCaptor<SellerProfileRejected> outboxCaptor = ArgumentCaptor.forClass(SellerProfileRejected.class);
        verify(outbox).store(outboxCaptor.capture());
        SellerProfileRejected storedEvent = outboxCaptor.getValue();
        assertThat(storedEvent.version()).isEqualTo(4L);
        assertThat(storedEvent.viewedVersion()).isEqualTo(3L);
        assertThat(storedEvent.accountId()).isEqualTo(ownerId);
        assertThat(storedEvent.profileId()).isEqualTo(SellerProfileId.of(profileId));
        assertThat(storedEvent.reason()).isEqualTo("Giấy tờ mờ");

        // verify(recorder).record đúng với SELLER_PROFILE_REJECTED và details 3 khóa
        verify(recorder).record(
                eq("SELLER_PROFILE_REJECTED"),
                eq(new AuditActor(adminId, "ADMIN")),
                eq(new AuditTarget("SELLER_PROFILE", profileId.toString())),
                eq(Map.of(
                        "viewedVersion", 3L,
                        "sellerAccountId", ownerId.toString(),
                        "rejectionReason", "Giấy tờ mờ"
                ))
        );

        // Mã event dùng ids() và thời điểm dùng clock()
        assertThat(storedEvent.getEventId()).isEqualTo(new UUID(0, 1).toString());
        assertThat(storedEvent.getOccurredOn()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("Lý do từ chối null ném REJECTION_REASON_REQUIRED, kho giả spy không save, outbox/recorder không gọi")
    void nullReasonThrowsRejectionReasonRequired() {
        FakeSellerProfileRepository spyRepo = spy(new FakeSellerProfileRepository());
        RejectSellerProfile useCase = new RejectSellerProfile(spyRepo, ids(), clock(), eventDispatcher);

        UUID profileId = UUID.fromString("00000000-0000-0000-0000-000000000010");
        UUID ownerId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID adminId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        SellerProfile profile = createPendingProfile(profileId, ownerId, 3L);
        spyRepo.save(profile);
        clearInvocations(spyRepo);

        RejectSellerProfile.Command command = new RejectSellerProfile.Command(admin(adminId), profileId, 3L, null);

        assertThatThrownBy(() -> useCase.handle(command))
                .isInstanceOf(SellerFieldException.class)
                .satisfies(ex -> assertThat(((SellerFieldException) ex).getErrorCode())
                        .isEqualTo(SellerErrorCode.REJECTION_REASON_REQUIRED));

        verify(spyRepo, never()).save(any());
        verifyNoInteractions(outbox, recorder);
    }

    @Test
    @DisplayName("Lý do từ chối rỗng/blank ném REJECTION_REASON_REQUIRED, kho giả spy không save, outbox/recorder không gọi")
    void blankReasonThrowsRejectionReasonRequired() {
        FakeSellerProfileRepository spyRepo = spy(new FakeSellerProfileRepository());
        RejectSellerProfile useCase = new RejectSellerProfile(spyRepo, ids(), clock(), eventDispatcher);

        UUID profileId = UUID.fromString("00000000-0000-0000-0000-000000000010");
        UUID ownerId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID adminId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        SellerProfile profile = createPendingProfile(profileId, ownerId, 3L);
        spyRepo.save(profile);
        clearInvocations(spyRepo);

        RejectSellerProfile.Command command = new RejectSellerProfile.Command(admin(adminId), profileId, 3L, "   ");

        assertThatThrownBy(() -> useCase.handle(command))
                .isInstanceOf(SellerFieldException.class)
                .satisfies(ex -> assertThat(((SellerFieldException) ex).getErrorCode())
                        .isEqualTo(SellerErrorCode.REJECTION_REASON_REQUIRED));

        verify(spyRepo, never()).save(any());
        verifyNoInteractions(outbox, recorder);
    }

    @Test
    @DisplayName("Lý do 501 ký tự ném SELLER_FIELD_INVALID")
    void reasonTooLongThrowsSellerFieldInvalid() {
        FakeSellerProfileRepository spyRepo = spy(new FakeSellerProfileRepository());
        RejectSellerProfile useCase = new RejectSellerProfile(spyRepo, ids(), clock(), eventDispatcher);

        UUID profileId = UUID.fromString("00000000-0000-0000-0000-000000000010");
        UUID ownerId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID adminId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        SellerProfile profile = createPendingProfile(profileId, ownerId, 3L);
        spyRepo.save(profile);
        clearInvocations(spyRepo);

        String longReason = "a".repeat(501);
        RejectSellerProfile.Command command = new RejectSellerProfile.Command(admin(adminId), profileId, 3L, longReason);

        assertThatThrownBy(() -> useCase.handle(command))
                .isInstanceOf(SellerFieldException.class)
                .satisfies(ex -> assertThat(((SellerFieldException) ex).getErrorCode())
                        .isEqualTo(SellerErrorCode.SELLER_FIELD_INVALID));

        verify(spyRepo, never()).save(any());
        verifyNoInteractions(outbox, recorder);
    }

    @Test
    @DisplayName("Hồ sơ không tồn tại ném SELLER_PROFILE_NOT_FOUND, không gọi outbox và recorder")
    void profileNotFound() {
        UUID profileId = UUID.fromString("00000000-0000-0000-0000-000000000099");
        UUID adminId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        RejectSellerProfile.Command command = new RejectSellerProfile.Command(
                admin(adminId), profileId, 3L, "Giấy tờ mờ"
        );

        assertThatThrownBy(() -> rejectSellerProfile.handle(command))
                .isInstanceOf(DomainException.class)
                .satisfies(ex -> assertThat(((DomainException) ex).getErrorCode())
                        .isEqualTo(SellerErrorCode.SELLER_PROFILE_NOT_FOUND));

        verifyNoInteractions(outbox, recorder);
    }

    @Test
    @DisplayName("Version null ném SellerFieldException(SELLER_FIELD_INVALID, version) và chưa đọc kho")
    void versionNullThrowsFieldExceptionWithoutReadingRepository() {
        SellerProfileRepository mockRepo = mock(SellerProfileRepository.class);
        RejectSellerProfile useCase = new RejectSellerProfile(mockRepo, ids(), clock(), eventDispatcher);

        UUID profileId = UUID.fromString("00000000-0000-0000-0000-000000000010");
        UUID adminId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        RejectSellerProfile.Command command = new RejectSellerProfile.Command(
                admin(adminId), profileId, null, "Giấy tờ mờ"
        );

        assertThatThrownBy(() -> useCase.handle(command))
                .isInstanceOf(SellerFieldException.class)
                .satisfies(ex -> {
                    SellerFieldException fe = (SellerFieldException) ex;
                    assertThat(fe.getErrorCode()).isEqualTo(SellerErrorCode.SELLER_FIELD_INVALID);
                    assertThat(fe.field()).isEqualTo("version");
                });

        verifyNoInteractions(mockRepo, outbox, recorder);
    }

    @Test
    @DisplayName("Người gọi không phải ADMIN (USER) bị ném SELLER_ROLE_NOT_ALLOWED, không gọi kho, outbox, recorder")
    void userRoleNotAllowed() {
        SellerProfileRepository mockRepo = mock(SellerProfileRepository.class);
        RejectSellerProfile useCase = new RejectSellerProfile(mockRepo, ids(), clock(), eventDispatcher);

        UUID profileId = UUID.fromString("00000000-0000-0000-0000-000000000010");
        UUID userId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        RejectSellerProfile.Command command = new RejectSellerProfile.Command(
                user(userId), profileId, 3L, "Giấy tờ mờ"
        );

        assertThatThrownBy(() -> useCase.handle(command))
                .isInstanceOf(DomainException.class)
                .satisfies(ex -> assertThat(((DomainException) ex).getErrorCode())
                        .isEqualTo(SellerErrorCode.SELLER_ROLE_NOT_ALLOWED));

        verifyNoInteractions(mockRepo, outbox, recorder);
    }

    @Test
    @DisplayName("Người gọi có vai trò rỗng bị ném SELLER_ROLE_NOT_ALLOWED")
    void emptyRoleNotAllowed() {
        SellerProfileRepository mockRepo = mock(SellerProfileRepository.class);
        RejectSellerProfile useCase = new RejectSellerProfile(mockRepo, ids(), clock(), eventDispatcher);

        UUID profileId = UUID.fromString("00000000-0000-0000-0000-000000000010");
        UUID actorId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        RejectSellerProfile.Command command = new RejectSellerProfile.Command(
                new SellerActor(actorId, ""), profileId, 3L, "Giấy tờ mờ"
        );

        assertThatThrownBy(() -> useCase.handle(command))
                .isInstanceOf(DomainException.class)
                .satisfies(ex -> assertThat(((DomainException) ex).getErrorCode())
                        .isEqualTo(SellerErrorCode.SELLER_ROLE_NOT_ALLOWED));

        verifyNoInteractions(mockRepo, outbox, recorder);
    }

    @Test
    @DisplayName("Hồ sơ đã APPROVED ném INVALID_STATE, không gọi outbox và recorder")
    void alreadyApprovedThrowsInvalidState() {
        UUID profileId = UUID.fromString("00000000-0000-0000-0000-000000000010");
        UUID ownerId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID adminId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        SellerProfile profile = createApprovedProfile(profileId, ownerId, 3L);
        repository.save(profile);

        RejectSellerProfile.Command command = new RejectSellerProfile.Command(
                admin(adminId), profileId, 3L, "Giấy tờ mờ"
        );

        assertThatThrownBy(() -> rejectSellerProfile.handle(command))
                .isInstanceOf(DomainException.class)
                .satisfies(ex -> assertThat(((DomainException) ex).getErrorCode())
                        .isEqualTo(SellerErrorCode.INVALID_STATE));

        verifyNoInteractions(outbox, recorder);
    }

    @Test
    @DisplayName("Phiên bản lệch (viewed 2L trên hồ sơ 3L) ném STALE_VERSION, kho không save, hồ sơ giữ PENDING_REVIEW")
    void staleVersionThrowsAndNeverSaves() {
        FakeSellerProfileRepository spyRepo = spy(new FakeSellerProfileRepository());
        RejectSellerProfile useCase = new RejectSellerProfile(spyRepo, ids(), clock(), eventDispatcher);

        UUID profileId = UUID.fromString("00000000-0000-0000-0000-000000000010");
        UUID ownerId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID adminId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        SellerProfile profile = createPendingProfile(profileId, ownerId, 3L);
        spyRepo.save(profile);
        clearInvocations(spyRepo);

        RejectSellerProfile.Command command = new RejectSellerProfile.Command(
                admin(adminId), profileId, 2L, "Giấy tờ mờ"
        );

        assertThatThrownBy(() -> useCase.handle(command))
                .isInstanceOf(DomainException.class)
                .satisfies(ex -> assertThat(((DomainException) ex).getErrorCode())
                        .isEqualTo(SellerErrorCode.STALE_VERSION));

        verify(spyRepo, never()).save(any());
        verifyNoInteractions(outbox, recorder);

        SellerProfile stored = spyRepo.findById(SellerProfileId.of(profileId)).orElseThrow();
        assertThat(stored.status()).isEqualTo(SellerProfileStatus.PENDING_REVIEW);
    }

    @Test
    @DisplayName("Bộ ghi kiểm toán ném lỗi thì use case ném chính ngoại lệ đó không nuốt")
    void auditRecorderThrowsPropagatesException() {
        UUID profileId = UUID.fromString("00000000-0000-0000-0000-000000000010");
        UUID ownerId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID adminId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        SellerProfile profile = createPendingProfile(profileId, ownerId, 3L);
        repository.save(profile);

        doThrow(new IllegalStateException("kiểm toán lỗi"))
                .when(recorder).record(any(), any(), any(), any());

        RejectSellerProfile.Command command = new RejectSellerProfile.Command(
                admin(adminId), profileId, 3L, "Giấy tờ mờ"
        );

        assertThatThrownBy(() -> rejectSellerProfile.handle(command))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("kiểm toán lỗi");
    }
}
