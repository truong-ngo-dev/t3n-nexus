package vn.t3nexus.seller.domain.sellerprofile;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.t3nexus.lib.common.domain.model.DomainEvent;
import vn.t3nexus.lib.common.domain.exception.DomainException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Kiểm thử quyết định duyệt hoặc từ chối hồ sơ người bán (approve, reject, version).
 */
class SellerProfileDecisionTest {

    private static final Instant NOW = Instant.parse("2026-10-26T03:04:05.123456789Z");
    private static final Instant NOW_MICROS = Instant.parse("2026-10-26T03:04:05.123456Z");

    private static final UUID OWNER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ADMIN_ID = UUID.fromString("99999999-9999-9999-9999-999999999999");
    private static final UUID OTHER_USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private LegalInfo createValidIndividualLegalInfo() {
        return LegalInfo.of(
                SellerType.INDIVIDUAL,
                "Nguyễn Văn A",
                null,
                "079123456789",
                null,
                "Số 1 Đại Cồ Việt, Hai Bà Trưng, Hà Nội"
        );
    }

    private ShopInfo createValidShopInfo(String name) {
        return ShopInfo.of(
                name,
                "Mô tả",
                Contact.of("Nguyễn Văn A", "0912345678", null)
        );
    }

    private SellerActor createActor(UUID accountId, String role) {
        return new SellerActor(accountId, role);
    }

    private SellerProfile profile(SellerProfileStatus status, Long version) {
        SellerProfileId id = SellerProfileId.of(UUID.randomUUID());
        LegalInfo legalInfo = createValidIndividualLegalInfo();
        ShopInfo shopInfo = createValidShopInfo("Cửa Hàng Xanh");

        String rejectionReason;
        Instant reviewedAt;
        UUID reviewedBy;

        if (status == SellerProfileStatus.PENDING_REVIEW) {
            rejectionReason = null;
            reviewedAt = null;
            reviewedBy = null;
        } else if (status == SellerProfileStatus.APPROVED) {
            rejectionReason = null;
            reviewedAt = NOW;
            reviewedBy = ADMIN_ID;
        } else if (status == SellerProfileStatus.REJECTED) {
            rejectionReason = "Giấy tờ không rõ";
            reviewedAt = NOW;
            reviewedBy = ADMIN_ID;
        } else {
            throw new IllegalArgumentException("Unsupported status: " + status);
        }

        return SellerProfile.restore(
                id,
                OWNER_ID,
                status,
                legalInfo,
                shopInfo,
                rejectionReason,
                NOW,
                reviewedAt,
                reviewedBy,
                NOW,
                NOW,
                version
        );
    }

    // ==========================================
    // 1. version()
    // ==========================================

    @Test
    @DisplayName("version() trả phiên bản đã nạp từ restore; trả null khi dựng từ submit")
    void testVersionBehavior() {
        // restore(..., 7L) thì version() bằng 7L
        SellerProfile restored = profile(SellerProfileStatus.PENDING_REVIEW, 7L);
        assertThat(restored.version()).isEqualTo(7L);

        // hồ sơ từ submit(...) thì version() là null
        SellerProfile submitted = SellerProfile.submit(
                SellerProfileId.of(UUID.randomUUID()),
                createActor(OWNER_ID, "USER"),
                createValidIndividualLegalInfo(),
                createValidShopInfo("Cửa Hàng Xanh"),
                UUID.randomUUID(),
                NOW
        );
        assertThat(submitted.version()).isNull();
    }

    // ==========================================
    // approve()
    // ==========================================

    @Test
    @DisplayName("approve thành công: cập nhật trạng thái, reviewedBy, reviewedAt, updatedAt và phát SellerProfileApproved")
    void testApproveSuccess() {
        SellerProfile sellerProfile = profile(SellerProfileStatus.PENDING_REVIEW, 3L);
        SellerProfileId profileId = sellerProfile.getId();
        LegalInfo expectedLegal = sellerProfile.legalInfo();
        ShopInfo expectedShop = sellerProfile.shopInfo();
        Instant expectedSubmittedAt = sellerProfile.submittedAt();
        Instant expectedCreatedAt = sellerProfile.createdAt();
        UUID expectedOwnerId = sellerProfile.ownerAccountId();

        SellerActor admin = createActor(ADMIN_ID, "ADMIN");
        UUID eventId = UUID.randomUUID();

        sellerProfile.approve(admin, 3L, eventId, NOW);

        // Kiểm tra thuộc tính
        assertThat(sellerProfile.status()).isEqualTo(SellerProfileStatus.APPROVED);
        assertThat(sellerProfile.reviewedBy()).isEqualTo(ADMIN_ID);
        assertThat(sellerProfile.reviewedAt()).isEqualTo(NOW_MICROS);
        assertThat(sellerProfile.updatedAt()).isEqualTo(NOW_MICROS);
        assertThat(sellerProfile.rejectionReason()).isNull();

        // Không đổi
        assertThat(sellerProfile.legalInfo()).isEqualTo(expectedLegal);
        assertThat(sellerProfile.shopInfo()).isEqualTo(expectedShop);
        assertThat(sellerProfile.submittedAt()).isEqualTo(expectedSubmittedAt);
        assertThat(sellerProfile.createdAt()).isEqualTo(expectedCreatedAt);
        assertThat(sellerProfile.ownerAccountId()).isEqualTo(expectedOwnerId);
        assertThat(sellerProfile.getId()).isEqualTo(profileId);
        assertThat(sellerProfile.version()).isEqualTo(3L);

        // Domain events
        Collection<DomainEvent> events = sellerProfile.getDomainEvents();
        assertThat(events).hasSize(1);
        DomainEvent firstEvent = events.iterator().next();
        assertThat(firstEvent).isInstanceOf(SellerProfileApproved.class);

        SellerProfileApproved approvedEvent = (SellerProfileApproved) firstEvent;
        assertThat(approvedEvent.getEventId()).isEqualTo(eventId.toString());
        assertThat(approvedEvent.getOccurredOn()).isEqualTo(NOW);
        assertThat(approvedEvent.accountId()).isEqualTo(OWNER_ID);
        assertThat(approvedEvent.profileId()).isEqualTo(profileId);
        assertThat(approvedEvent.version()).isEqualTo(4L);
        assertThat(approvedEvent.reviewedBy()).isEqualTo(ADMIN_ID);
        assertThat(approvedEvent.viewedVersion()).isEqualTo(3L);
    }

    @Test
    @DisplayName("approve từ APPROVED hoặc REJECTED ném INVALID_STATE, không đổi trạng thái hay sinh event")
    void testApproveFromNonPendingState() {
        SellerActor admin = createActor(ADMIN_ID, "ADMIN");
        UUID eventId = UUID.randomUUID();

        // Từ APPROVED
        SellerProfile approved = profile(SellerProfileStatus.APPROVED, 3L);
        Instant prevReviewedAt = approved.reviewedAt();
        assertThatThrownBy(() -> approved.approve(admin, 3L, eventId, NOW))
                .isInstanceOf(DomainException.class)
                .satisfies(ex -> assertThat(((DomainException) ex).getErrorCode()).isEqualTo(SellerErrorCode.INVALID_STATE));
        assertThat(approved.status()).isEqualTo(SellerProfileStatus.APPROVED);
        assertThat(approved.reviewedAt()).isEqualTo(prevReviewedAt);
        assertThat(approved.getDomainEvents()).isEmpty();

        // Từ REJECTED
        SellerProfile rejected = profile(SellerProfileStatus.REJECTED, 3L);
        Instant prevRejectedReviewedAt = rejected.reviewedAt();
        assertThatThrownBy(() -> rejected.approve(admin, 3L, eventId, NOW))
                .isInstanceOf(DomainException.class)
                .satisfies(ex -> assertThat(((DomainException) ex).getErrorCode()).isEqualTo(SellerErrorCode.INVALID_STATE));
        assertThat(rejected.status()).isEqualTo(SellerProfileStatus.REJECTED);
        assertThat(rejected.reviewedAt()).isEqualTo(prevRejectedReviewedAt);
        assertThat(rejected.getDomainEvents()).isEmpty();
    }

    @Test
    @DisplayName("approve với viewedVersion lệch ném STALE_VERSION, trạng thái giữ nguyên và không có event")
    void testApproveWithStaleVersion() {
        SellerActor admin = createActor(ADMIN_ID, "ADMIN");
        UUID eventId = UUID.randomUUID();

        SellerProfile profile1 = profile(SellerProfileStatus.PENDING_REVIEW, 3L);
        assertThatThrownBy(() -> profile1.approve(admin, 2L, eventId, NOW))
                .isInstanceOf(DomainException.class)
                .satisfies(ex -> assertThat(((DomainException) ex).getErrorCode()).isEqualTo(SellerErrorCode.STALE_VERSION));
        assertThat(profile1.status()).isEqualTo(SellerProfileStatus.PENDING_REVIEW);
        assertThat(profile1.getDomainEvents()).isEmpty();

        SellerProfile profile2 = profile(SellerProfileStatus.PENDING_REVIEW, 3L);
        assertThatThrownBy(() -> profile2.approve(admin, 4L, eventId, NOW))
                .isInstanceOf(DomainException.class)
                .satisfies(ex -> assertThat(((DomainException) ex).getErrorCode()).isEqualTo(SellerErrorCode.STALE_VERSION));
        assertThat(profile2.status()).isEqualTo(SellerProfileStatus.PENDING_REVIEW);
        assertThat(profile2.getDomainEvents()).isEmpty();
    }

    @Test
    @DisplayName("approve kiểm trạng thái trước viewedVersion: APPROVED với viewedVersion cũ ném INVALID_STATE")
    void testApproveStateCheckedBeforeVersion() {
        SellerActor admin = createActor(ADMIN_ID, "ADMIN");
        SellerProfile approved = profile(SellerProfileStatus.APPROVED, 5L);

        assertThatThrownBy(() -> approved.approve(admin, 3L, UUID.randomUUID(), NOW))
                .isInstanceOf(DomainException.class)
                .satisfies(ex -> assertThat(((DomainException) ex).getErrorCode()).isEqualTo(SellerErrorCode.INVALID_STATE));
    }

    @Test
    @DisplayName("approve kiểm vai ADMIN trước trạng thái: không phải ADMIN ném SELLER_ROLE_NOT_ALLOWED")
    void testApproveActorRoleValidation() {
        SellerActor user = createActor(OTHER_USER_ID, "USER");
        SellerActor emptyRole = createActor(OTHER_USER_ID, "");
        UUID eventId = UUID.randomUUID();

        // USER gọi trên PENDING_REVIEW khớp version -> SELLER_ROLE_NOT_ALLOWED
        SellerProfile pending = profile(SellerProfileStatus.PENDING_REVIEW, 3L);
        assertThatThrownBy(() -> pending.approve(user, 3L, eventId, NOW))
                .isInstanceOf(DomainException.class)
                .satisfies(ex -> assertThat(((DomainException) ex).getErrorCode()).isEqualTo(SellerErrorCode.SELLER_ROLE_NOT_ALLOWED));

        // vai rỗng gọi trên PENDING_REVIEW khớp version -> SELLER_ROLE_NOT_ALLOWED
        assertThatThrownBy(() -> pending.approve(emptyRole, 3L, eventId, NOW))
                .isInstanceOf(DomainException.class)
                .satisfies(ex -> assertThat(((DomainException) ex).getErrorCode()).isEqualTo(SellerErrorCode.SELLER_ROLE_NOT_ALLOWED));

        // USER gọi trên APPROVED -> vẫn SELLER_ROLE_NOT_ALLOWED (kiểm vai trước trạng thái)
        SellerProfile approved = profile(SellerProfileStatus.APPROVED, 3L);
        assertThatThrownBy(() -> approved.approve(user, 3L, eventId, NOW))
                .isInstanceOf(DomainException.class)
                .satisfies(ex -> assertThat(((DomainException) ex).getErrorCode()).isEqualTo(SellerErrorCode.SELLER_ROLE_NOT_ALLOWED));
    }

    @Test
    @DisplayName("approve ném NullPointerException khi tham số null")
    void testApproveNullParameters() {
        SellerProfile pending = profile(SellerProfileStatus.PENDING_REVIEW, 3L);
        SellerActor admin = createActor(ADMIN_ID, "ADMIN");
        UUID eventId = UUID.randomUUID();

        assertThatThrownBy(() -> pending.approve(null, 3L, eventId, NOW))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> pending.approve(admin, 3L, null, NOW))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> pending.approve(admin, 3L, eventId, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("approve trên hồ sơ dựng từ submit (version null) ném IllegalStateException")
    void testApproveOnProfileWithNullVersion() {
        SellerProfile submitted = SellerProfile.submit(
                SellerProfileId.of(UUID.randomUUID()),
                createActor(OWNER_ID, "USER"),
                createValidIndividualLegalInfo(),
                createValidShopInfo("Cửa Hàng Xanh"),
                UUID.randomUUID(),
                NOW
        );
        SellerActor admin = createActor(ADMIN_ID, "ADMIN");

        assertThatThrownBy(() -> submitted.approve(admin, 0L, UUID.randomUUID(), NOW))
                .isInstanceOf(IllegalStateException.class);
    }

    // ==========================================
    // reject()
    // ==========================================

    @Test
    @DisplayName("reject thành công: cập nhật REJECTED, rejectionReason cắt khoảng trắng, và phát SellerProfileRejected")
    void testRejectSuccess() {
        SellerProfile sellerProfile = profile(SellerProfileStatus.PENDING_REVIEW, 3L);
        SellerProfileId profileId = sellerProfile.getId();
        LegalInfo expectedLegal = sellerProfile.legalInfo();
        ShopInfo expectedShop = sellerProfile.shopInfo();
        Instant expectedSubmittedAt = sellerProfile.submittedAt();
        Instant expectedCreatedAt = sellerProfile.createdAt();
        UUID expectedOwnerId = sellerProfile.ownerAccountId();

        SellerActor admin = createActor(ADMIN_ID, "ADMIN");
        UUID eventId = UUID.randomUUID();

        sellerProfile.reject(admin, 3L, "  Giấy tờ mờ  ", eventId, NOW);

        // Kiểm tra thuộc tính
        assertThat(sellerProfile.status()).isEqualTo(SellerProfileStatus.REJECTED);
        assertThat(sellerProfile.rejectionReason()).isEqualTo("Giấy tờ mờ");
        assertThat(sellerProfile.reviewedBy()).isEqualTo(ADMIN_ID);
        assertThat(sellerProfile.reviewedAt()).isEqualTo(NOW_MICROS);
        assertThat(sellerProfile.updatedAt()).isEqualTo(NOW_MICROS);

        // Không đổi
        assertThat(sellerProfile.legalInfo()).isEqualTo(expectedLegal);
        assertThat(sellerProfile.shopInfo()).isEqualTo(expectedShop);
        assertThat(sellerProfile.submittedAt()).isEqualTo(expectedSubmittedAt);
        assertThat(sellerProfile.createdAt()).isEqualTo(expectedCreatedAt);
        assertThat(sellerProfile.ownerAccountId()).isEqualTo(expectedOwnerId);
        assertThat(sellerProfile.getId()).isEqualTo(profileId);
        assertThat(sellerProfile.version()).isEqualTo(3L);

        // Domain events
        Collection<DomainEvent> events = sellerProfile.getDomainEvents();
        assertThat(events).hasSize(1);
        DomainEvent firstEvent = events.iterator().next();
        assertThat(firstEvent).isInstanceOf(SellerProfileRejected.class);

        SellerProfileRejected rejectedEvent = (SellerProfileRejected) firstEvent;
        assertThat(rejectedEvent.getEventId()).isEqualTo(eventId.toString());
        assertThat(rejectedEvent.getOccurredOn()).isEqualTo(NOW);
        assertThat(rejectedEvent.accountId()).isEqualTo(OWNER_ID);
        assertThat(rejectedEvent.profileId()).isEqualTo(profileId);
        assertThat(rejectedEvent.version()).isEqualTo(4L);
        assertThat(rejectedEvent.reviewedBy()).isEqualTo(ADMIN_ID);
        assertThat(rejectedEvent.viewedVersion()).isEqualTo(3L);
        assertThat(rejectedEvent.reason()).isEqualTo("Giấy tờ mờ");
    }

    @Test
    @DisplayName("reject kiểm tra tính hợp lệ của lý do: null, rỗng, độ dài tối đa 500 ký tự")
    void testRejectReasonValidation() {
        SellerProfile sellerProfile = profile(SellerProfileStatus.PENDING_REVIEW, 3L);
        SellerActor admin = createActor(ADMIN_ID, "ADMIN");
        UUID eventId = UUID.randomUUID();

        // null -> REJECTION_REASON_REQUIRED
        assertThatThrownBy(() -> sellerProfile.reject(admin, 3L, null, eventId, NOW))
                .isInstanceOf(SellerFieldException.class)
                .satisfies(ex -> {
                    SellerFieldException sfe = (SellerFieldException) ex;
                    assertThat(sfe.getErrorCode()).isEqualTo(SellerErrorCode.REJECTION_REASON_REQUIRED);
                    assertThat(sfe.field()).isEqualTo("reason");
                });

        // "" -> REJECTION_REASON_REQUIRED
        assertThatThrownBy(() -> sellerProfile.reject(admin, 3L, "", eventId, NOW))
                .isInstanceOf(SellerFieldException.class)
                .satisfies(ex -> {
                    SellerFieldException sfe = (SellerFieldException) ex;
                    assertThat(sfe.getErrorCode()).isEqualTo(SellerErrorCode.REJECTION_REASON_REQUIRED);
                    assertThat(sfe.field()).isEqualTo("reason");
                });

        // "   " -> REJECTION_REASON_REQUIRED
        assertThatThrownBy(() -> sellerProfile.reject(admin, 3L, "   ", eventId, NOW))
                .isInstanceOf(SellerFieldException.class)
                .satisfies(ex -> {
                    SellerFieldException sfe = (SellerFieldException) ex;
                    assertThat(sfe.getErrorCode()).isEqualTo(SellerErrorCode.REJECTION_REASON_REQUIRED);
                    assertThat(sfe.field()).isEqualTo("reason");
                });

        // Khi lỗi: trạng thái không đổi, không có event
        assertThat(sellerProfile.status()).isEqualTo(SellerProfileStatus.PENDING_REVIEW);
        assertThat(sellerProfile.getDomainEvents()).isEmpty();

        // 501 ký tự -> SELLER_FIELD_INVALID
        String long501 = "a".repeat(501);
        assertThatThrownBy(() -> sellerProfile.reject(admin, 3L, long501, eventId, NOW))
                .isInstanceOf(SellerFieldException.class)
                .satisfies(ex -> {
                    SellerFieldException sfe = (SellerFieldException) ex;
                    assertThat(sfe.getErrorCode()).isEqualTo(SellerErrorCode.SELLER_FIELD_INVALID);
                    assertThat(sfe.field()).isEqualTo("reason");
                });
        assertThat(sellerProfile.status()).isEqualTo(SellerProfileStatus.PENDING_REVIEW);
        assertThat(sellerProfile.getDomainEvents()).isEmpty();

        // Đúng 500 ký tự thành công
        String exact500 = "a".repeat(500);
        SellerProfile profile500 = profile(SellerProfileStatus.PENDING_REVIEW, 3L);
        profile500.reject(admin, 3L, exact500, eventId, NOW);
        assertThat(profile500.status()).isEqualTo(SellerProfileStatus.REJECTED);
        assertThat(profile500.rejectionReason()).isEqualTo(exact500);

        // Có khoảng trắng đầu cuối mà phần còn lại đúng 500 ký tự thì thành công (đo sau khi cắt)
        String padded500 = "   " + "b".repeat(500) + "  ";
        SellerProfile profilePadded = profile(SellerProfileStatus.PENDING_REVIEW, 3L);
        profilePadded.reject(admin, 3L, padded500, UUID.randomUUID(), NOW);
        assertThat(profilePadded.status()).isEqualTo(SellerProfileStatus.REJECTED);
        assertThat(profilePadded.rejectionReason()).isEqualTo("b".repeat(500));
    }

    @Test
    @DisplayName("reject từ APPROVED hoặc REJECTED ném INVALID_STATE khi lý do hợp lệ và version khớp")
    void testRejectFromNonPendingState() {
        SellerActor admin = createActor(ADMIN_ID, "ADMIN");
        UUID eventId = UUID.randomUUID();

        SellerProfile approved = profile(SellerProfileStatus.APPROVED, 3L);
        assertThatThrownBy(() -> approved.reject(admin, 3L, "Lý do từ chối", eventId, NOW))
                .isInstanceOf(DomainException.class)
                .satisfies(ex -> assertThat(((DomainException) ex).getErrorCode()).isEqualTo(SellerErrorCode.INVALID_STATE));

        SellerProfile rejected = profile(SellerProfileStatus.REJECTED, 3L);
        assertThatThrownBy(() -> rejected.reject(admin, 3L, "Lý do từ chối", eventId, NOW))
                .isInstanceOf(DomainException.class)
                .satisfies(ex -> assertThat(((DomainException) ex).getErrorCode()).isEqualTo(SellerErrorCode.INVALID_STATE));
    }

    @Test
    @DisplayName("reject với viewedVersion khác ném STALE_VERSION; không đổi trạng thái, không event")
    void testRejectWithStaleVersion() {
        SellerActor admin = createActor(ADMIN_ID, "ADMIN");
        UUID eventId = UUID.randomUUID();

        SellerProfile pending = profile(SellerProfileStatus.PENDING_REVIEW, 3L);
        assertThatThrownBy(() -> pending.reject(admin, 2L, "Lý do từ chối", eventId, NOW))
                .isInstanceOf(DomainException.class)
                .satisfies(ex -> assertThat(((DomainException) ex).getErrorCode()).isEqualTo(SellerErrorCode.STALE_VERSION));
        assertThat(pending.status()).isEqualTo(SellerProfileStatus.PENDING_REVIEW);
        assertThat(pending.getDomainEvents()).isEmpty();
    }

    @Test
    @DisplayName("reject thứ tự kiểm: vai -> lý do -> trạng thái -> phiên bản")
    void testRejectValidationOrder() {
        UUID eventId = UUID.randomUUID();

        // (a) USER + lý do rỗng -> SELLER_ROLE_NOT_ALLOWED
        SellerProfile pending = profile(SellerProfileStatus.PENDING_REVIEW, 3L);
        SellerActor user = createActor(OTHER_USER_ID, "USER");
        assertThatThrownBy(() -> pending.reject(user, 3L, "", eventId, NOW))
                .isInstanceOf(DomainException.class)
                .satisfies(ex -> assertThat(((DomainException) ex).getErrorCode()).isEqualTo(SellerErrorCode.SELLER_ROLE_NOT_ALLOWED));

        // (b) ADMIN + lý do rỗng trên hồ sơ APPROVED -> REJECTION_REASON_REQUIRED
        SellerProfile approved = profile(SellerProfileStatus.APPROVED, 3L);
        SellerActor admin = createActor(ADMIN_ID, "ADMIN");
        assertThatThrownBy(() -> approved.reject(admin, 3L, "", eventId, NOW))
                .isInstanceOf(SellerFieldException.class)
                .satisfies(ex -> {
                    SellerFieldException sfe = (SellerFieldException) ex;
                    assertThat(sfe.getErrorCode()).isEqualTo(SellerErrorCode.REJECTION_REASON_REQUIRED);
                    assertThat(sfe.field()).isEqualTo("reason");
                });

        // (c) ADMIN + lý do hợp lệ trên hồ sơ APPROVED với viewedVersion cũ -> INVALID_STATE
        assertThatThrownBy(() -> approved.reject(admin, 1L, "Lý do hợp lệ", eventId, NOW))
                .isInstanceOf(DomainException.class)
                .satisfies(ex -> assertThat(((DomainException) ex).getErrorCode()).isEqualTo(SellerErrorCode.INVALID_STATE));
    }

    @Test
    @DisplayName("reject ném NullPointerException khi tham số tham chiếu null")
    void testRejectNullParameters() {
        SellerProfile pending = profile(SellerProfileStatus.PENDING_REVIEW, 3L);
        SellerActor admin = createActor(ADMIN_ID, "ADMIN");
        UUID eventId = UUID.randomUUID();

        // 1. actor null -> NullPointerException
        assertThatThrownBy(() -> pending.reject(null, 3L, "Lý do", eventId, NOW))
                .isInstanceOf(NullPointerException.class);

        // eventId null -> NullPointerException
        assertThatThrownBy(() -> pending.reject(admin, 3L, "Lý do", null, NOW))
                .isInstanceOf(NullPointerException.class);

        // now null -> NullPointerException
        assertThatThrownBy(() -> pending.reject(admin, 3L, "Lý do", eventId, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("Sau reject rồi chủ hồ sơ gọi resubmit(...) hợp lệ: hồ sơ về PENDING_REVIEW và rejectionReason là null")
    void testResubmitAfterReject() {
        SellerProfile pending = profile(SellerProfileStatus.PENDING_REVIEW, 3L);
        SellerActor admin = createActor(ADMIN_ID, "ADMIN");
        UUID eventId = UUID.randomUUID();

        pending.reject(admin, 3L, "Giấy tờ không rõ", eventId, NOW);
        assertThat(pending.status()).isEqualTo(SellerProfileStatus.REJECTED);
        assertThat(pending.rejectionReason()).isEqualTo("Giấy tờ không rõ");

        // Chủ gọi resubmit
        SellerActor owner = createActor(OWNER_ID, "USER");
        LegalInfo newLegal = createValidIndividualLegalInfo();
        ShopInfo newShop = createValidShopInfo("Cửa Hàng Mới");
        Instant resubmitTime = NOW.plusSeconds(3600);

        pending.resubmit(owner, newLegal, newShop, UUID.randomUUID(), resubmitTime);

        assertThat(pending.status()).isEqualTo(SellerProfileStatus.PENDING_REVIEW);
        assertThat(pending.rejectionReason()).isNull();
        assertThat(pending.reviewedAt()).isNull();
        assertThat(pending.reviewedBy()).isNull();
    }
}
