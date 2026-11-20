package vn.t3nexus.seller.domain.sellerprofile;

import org.junit.jupiter.api.Test;
import vn.t3nexus.lib.common.domain.exception.DomainException;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Kiểm thử Aggregate Root SellerProfile.
 */
class SellerProfileTest {

    private static final Instant NOW = Instant.parse("2026-10-26T03:04:05.123456789Z");
    private static final Instant NOW_MICROS = Instant.parse("2026-10-26T03:04:05.123456Z");

    private static final UUID OWNER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
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

    private LegalInfo createValidBusinessLegalInfo() {
        return LegalInfo.of(
                SellerType.BUSINESS,
                "Công ty TNHH T3N",
                "Trần Văn B",
                "079123456789",
                "0123456789012",
                "Số 1 Đại Cồ Việt, Hai Bà Trưng, Hà Nội"
        );
    }

    private ShopInfo createValidShopInfo(String name) {
        return ShopInfo.of(
                name,
                "Mô tả gian hàng",
                Contact.of("Nguyễn Văn A", "0912345678", null)
        );
    }

    private SellerActor createActor(UUID accountId, String role) {
        return new SellerActor(accountId, role);
    }

    // --- SUBMIT TESTS ---

    @Test
    void submitCreatesPendingReviewProfileWithTruncatedTimestamps() {
        SellerProfileId id = SellerProfileId.of(UUID.randomUUID());
        SellerActor actor = createActor(OWNER_ID, "USER");
        LegalInfo legalInfo = createValidIndividualLegalInfo();
        ShopInfo shopInfo = createValidShopInfo("Cửa Hàng Xanh");

        SellerProfile profile = SellerProfile.submit(id, actor, legalInfo, shopInfo, UUID.randomUUID(), NOW);

        assertThat(profile.getId()).isEqualTo(id);
        assertThat(profile.ownerAccountId()).isEqualTo(OWNER_ID);
        assertThat(profile.status()).isEqualTo(SellerProfileStatus.PENDING_REVIEW);
        assertThat(profile.sellerType()).isEqualTo(SellerType.INDIVIDUAL);
        assertThat(profile.legalInfo()).isEqualTo(legalInfo);
        assertThat(profile.shopInfo()).isEqualTo(shopInfo);
        assertThat(profile.rejectionReason()).isNull();
        assertThat(profile.reviewedAt()).isNull();
        assertThat(profile.reviewedBy()).isNull();

        // submittedAt, createdAt, updatedAt bằng NOW cắt micro giây
        assertThat(profile.submittedAt()).isEqualTo(NOW_MICROS);
        assertThat(profile.createdAt()).isEqualTo(NOW_MICROS);
        assertThat(profile.updatedAt()).isEqualTo(NOW_MICROS);
    }

    @Test
    void submitThrowsWhenActorIsAdminBeforeOtherChecks() {
        SellerProfileId id = SellerProfileId.of(UUID.randomUUID());
        SellerActor adminActor = createActor(OWNER_ID, "ADMIN");

        // actor ADMIN thì SELLER_ROLE_NOT_ALLOWED (và kiểm vai xảy ra trước mọi kiểm khác)
        assertThatThrownBy(() -> SellerProfile.submit(id, adminActor, null, null, UUID.randomUUID(), NOW))
                .isInstanceOf(DomainException.class)
                .satisfies(ex -> assertThat(((DomainException) ex).getErrorCode())
                        .isEqualTo(SellerErrorCode.SELLER_ROLE_NOT_ALLOWED));
    }

    @Test
    void submitRejectsNullParameters() {
        SellerProfileId id = SellerProfileId.of(UUID.randomUUID());
        SellerActor actor = createActor(OWNER_ID, "USER");
        LegalInfo legalInfo = createValidIndividualLegalInfo();
        ShopInfo shopInfo = createValidShopInfo("Cửa Hàng Xanh");

        assertThatThrownBy(() -> SellerProfile.submit(null, actor, legalInfo, shopInfo, UUID.randomUUID(), NOW))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> SellerProfile.submit(id, null, legalInfo, shopInfo, UUID.randomUUID(), NOW))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> SellerProfile.submit(id, actor, null, shopInfo, UUID.randomUUID(), NOW))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> SellerProfile.submit(id, actor, legalInfo, null, UUID.randomUUID(), NOW))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> SellerProfile.submit(id, actor, legalInfo, shopInfo, UUID.randomUUID(), null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void submitEmitsExactlyOneSubmittedEventCarryingOwnerProfileAndSubmittedAt() {
        SellerProfileId id = SellerProfileId.of(UUID.randomUUID());
        UUID eventId = UUID.randomUUID();
        SellerActor actor = createActor(OWNER_ID, "USER");

        SellerProfile profile = SellerProfile.submit(
                id, actor, createValidIndividualLegalInfo(), createValidShopInfo("Cửa Hàng Xanh"), eventId, NOW);

        assertThat(profile.getDomainEvents()).hasSize(1);
        SellerProfileSubmitted event = (SellerProfileSubmitted) profile.getDomainEvents().iterator().next();
        assertThat(event.getEventId()).isEqualTo(eventId.toString());
        assertThat(event.accountId()).isEqualTo(OWNER_ID);
        assertThat(event.profileId()).isEqualTo(id);
        // submittedAt trong event là giá trị đã cắt micro giây, đúng bằng submittedAt của hồ sơ; occurredOn giữ nguyên thời điểm gọi
        assertThat(event.submittedAt()).isEqualTo(NOW_MICROS).isEqualTo(profile.submittedAt());
        assertThat(event.getOccurredOn()).isEqualTo(NOW);
    }

    @Test
    void submitRejectsNullEventId() {
        SellerProfileId id = SellerProfileId.of(UUID.randomUUID());
        SellerActor actor = createActor(OWNER_ID, "USER");

        assertThatThrownBy(() -> SellerProfile.submit(
                id, actor, createValidIndividualLegalInfo(), createValidShopInfo("Cửa Hàng Xanh"), null, NOW))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void restoredProfileHasNoPendingEvents() {
        SellerProfile restored = SellerProfile.restore(
                SellerProfileId.of(UUID.randomUUID()), OWNER_ID, SellerProfileStatus.PENDING_REVIEW,
                createValidIndividualLegalInfo(), createValidShopInfo("Cửa Hàng Xanh"),
                null, NOW, null, null, NOW, NOW, 1L);

        // Dựng lại từ kho không được phát lại sự kiện đã đi từ lần nộp trước
        assertThat(restored.getDomainEvents()).isEmpty();
    }

    @Test
    void setIdThrowsUnsupportedOperationException() {
        SellerProfileId id = SellerProfileId.of(UUID.randomUUID());
        SellerActor actor = createActor(OWNER_ID, "USER");
        SellerProfile profile = SellerProfile.submit(id, actor, createValidIndividualLegalInfo(), createValidShopInfo("Cửa Hàng Xanh"), UUID.randomUUID(), NOW);

        assertThatThrownBy(() -> profile.setId(SellerProfileId.of(UUID.randomUUID())))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    // --- UPDATE SHOP INFO TESTS ---

    @Test
    void updateShopInfoSucceedsInPendingReviewAndApproved() {
        SellerProfileId id = SellerProfileId.of(UUID.randomUUID());
        SellerActor actor = createActor(OWNER_ID, "USER");
        Instant updateTime = NOW.plusSeconds(3600);
        Instant updateTimeMicros = updateTime.truncatedTo(java.time.temporal.ChronoUnit.MICROS);

        // PENDING_REVIEW đổi được
        SellerProfile profile = SellerProfile.submit(id, actor, createValidIndividualLegalInfo(), createValidShopInfo("Cửa Hàng A"), UUID.randomUUID(), NOW);
        ShopInfo newShopInfo = createValidShopInfo("Cửa Hàng Mới");
        boolean changed = profile.updateShopInfo(actor, newShopInfo, updateTime);
        assertThat(changed).isTrue();
        assertThat(profile.shopInfo()).isEqualTo(newShopInfo);
        assertThat(profile.updatedAt()).isEqualTo(updateTimeMicros);
        assertThat(profile.status()).isEqualTo(SellerProfileStatus.PENDING_REVIEW);

        // APPROVED đổi được
        SellerProfile approvedProfile = SellerProfile.restore(
                id, OWNER_ID, SellerProfileStatus.APPROVED,
                createValidIndividualLegalInfo(), createValidShopInfo("Cửa Hàng B"),
                null, NOW, NOW, UUID.randomUUID(), NOW, NOW, 1L
        );
        boolean changedApproved = approvedProfile.updateShopInfo(actor, newShopInfo, updateTime);
        assertThat(changedApproved).isTrue();
        assertThat(approvedProfile.shopInfo()).isEqualTo(newShopInfo);
        assertThat(approvedProfile.updatedAt()).isEqualTo(updateTimeMicros);
        assertThat(approvedProfile.status()).isEqualTo(SellerProfileStatus.APPROVED);
    }

    @Test
    void updateShopInfoInRejectedThrowsInvalidState() {
        SellerProfileId id = SellerProfileId.of(UUID.randomUUID());
        SellerActor actor = createActor(OWNER_ID, "USER");
        SellerProfile rejectedProfile = SellerProfile.restore(
                id, OWNER_ID, SellerProfileStatus.REJECTED,
                createValidIndividualLegalInfo(), createValidShopInfo("Cửa Hàng B"),
                "Không đạt", NOW, NOW, UUID.randomUUID(), NOW, NOW, 1L
        );

        assertThatThrownBy(() -> rejectedProfile.updateShopInfo(actor, createValidShopInfo("Cửa Hàng Mới"), NOW))
                .isInstanceOf(DomainException.class)
                .satisfies(ex -> assertThat(((DomainException) ex).getErrorCode())
                        .isEqualTo(SellerErrorCode.INVALID_STATE));
    }

    @Test
    void updateShopInfoWithEqualInfoReturnsFalseAndDoesNotChangeUpdatedAt() {
        SellerProfileId id = SellerProfileId.of(UUID.randomUUID());
        SellerActor actor = createActor(OWNER_ID, "USER");
        ShopInfo initialInfo = createValidShopInfo("Cửa Hàng A");
        SellerProfile profile = SellerProfile.submit(id, actor, createValidIndividualLegalInfo(), initialInfo, UUID.randomUUID(), NOW);

        // shopInfo y hệt cái cũ
        ShopInfo sameInfo = createValidShopInfo("Cửa Hàng A");
        boolean changed = profile.updateShopInfo(actor, sameInfo, NOW.plusSeconds(3600));

        assertThat(changed).isFalse();
        assertThat(profile.updatedAt()).isEqualTo(NOW_MICROS);
    }

    @Test
    void updateShopInfoChangingOnlyCaseOrDiacriticsIsAChange() {
        SellerProfileId id = SellerProfileId.of(UUID.randomUUID());
        SellerActor actor = createActor(OWNER_ID, "USER");
        SellerProfile profile = SellerProfile.submit(id, actor, createValidIndividualLegalInfo(), createValidShopInfo("Cửa Hàng A"), UUID.randomUUID(), NOW);

        // người bán sửa cách viết tên của chính mình (cùng khóa so sánh) vẫn là thay đổi
        boolean changed = profile.updateShopInfo(actor, createValidShopInfo("CỬA HÀNG A"), NOW.plusSeconds(3600));

        assertThat(changed).isTrue();
        assertThat(profile.shopInfo().shopName().display()).isEqualTo("CỬA HÀNG A");
    }

    @Test
    void updateShopInfoValidationOrder() {
        SellerProfileId id = SellerProfileId.of(UUID.randomUUID());
        SellerProfile rejectedProfile = SellerProfile.restore(
                id, OWNER_ID, SellerProfileStatus.REJECTED,
                createValidIndividualLegalInfo(), createValidShopInfo("Cửa Hàng B"),
                "Không đạt", NOW, NOW, UUID.randomUUID(), NOW, NOW, 1L
        );

        // actor ADMIN thì SELLER_ROLE_NOT_ALLOWED trước mọi kiểm khác (kể cả khi trạng thái REJECTED)
        SellerActor adminActor = createActor(OWNER_ID, "ADMIN");
        assertThatThrownBy(() -> rejectedProfile.updateShopInfo(adminActor, createValidShopInfo("Mới"), NOW))
                .isInstanceOf(DomainException.class)
                .satisfies(ex -> assertThat(((DomainException) ex).getErrorCode())
                        .isEqualTo(SellerErrorCode.SELLER_ROLE_NOT_ALLOWED));

        // actor USER khác chủ thì SELLER_PROFILE_NOT_FOUND, và kiểm chủ xảy ra trước kiểm trạng thái
        SellerActor otherUser = createActor(OTHER_USER_ID, "USER");
        assertThatThrownBy(() -> rejectedProfile.updateShopInfo(otherUser, createValidShopInfo("Mới"), NOW))
                .isInstanceOf(DomainException.class)
                .satisfies(ex -> assertThat(((DomainException) ex).getErrorCode())
                        .isEqualTo(SellerErrorCode.SELLER_PROFILE_NOT_FOUND));
    }

    // --- UPDATE LEGAL INFO TESTS ---

    @Test
    void updateLegalInfoSucceedsInPendingReviewAndUpdatesSellerTypeAndIdentityKey() {
        SellerProfileId id = SellerProfileId.of(UUID.randomUUID());
        SellerActor actor = createActor(OWNER_ID, "USER");
        SellerProfile profile = SellerProfile.submit(id, actor, createValidIndividualLegalInfo(), createValidShopInfo("Cửa Hàng A"), UUID.randomUUID(), NOW);

        LegalInfo businessInfo = createValidBusinessLegalInfo();
        Instant updateTime = NOW.plusSeconds(3600);
        boolean changed = profile.updateLegalInfo(actor, businessInfo, updateTime);

        assertThat(changed).isTrue();
        assertThat(profile.legalInfo()).isEqualTo(businessInfo);
        assertThat(profile.sellerType()).isEqualTo(SellerType.BUSINESS);
        assertThat(profile.legalInfo().identityKey()).isEqualTo("0123456789012");
        assertThat(profile.updatedAt()).isEqualTo(updateTime.truncatedTo(java.time.temporal.ChronoUnit.MICROS));
    }

    @Test
    void updateLegalInfoInApprovedThrowsLegalInfoLocked() {
        SellerProfileId id = SellerProfileId.of(UUID.randomUUID());
        SellerActor actor = createActor(OWNER_ID, "USER");
        SellerProfile approvedProfile = SellerProfile.restore(
                id, OWNER_ID, SellerProfileStatus.APPROVED,
                createValidIndividualLegalInfo(), createValidShopInfo("Cửa Hàng B"),
                null, NOW, NOW, UUID.randomUUID(), NOW, NOW, 1L
        );

        assertThatThrownBy(() -> approvedProfile.updateLegalInfo(actor, createValidBusinessLegalInfo(), NOW))
                .isInstanceOf(DomainException.class)
                .satisfies(ex -> assertThat(((DomainException) ex).getErrorCode())
                        .isEqualTo(SellerErrorCode.LEGAL_INFO_LOCKED));
    }

    @Test
    void updateLegalInfoInRejectedThrowsInvalidState() {
        SellerProfileId id = SellerProfileId.of(UUID.randomUUID());
        SellerActor actor = createActor(OWNER_ID, "USER");
        SellerProfile rejectedProfile = SellerProfile.restore(
                id, OWNER_ID, SellerProfileStatus.REJECTED,
                createValidIndividualLegalInfo(), createValidShopInfo("Cửa Hàng B"),
                "Không đạt", NOW, NOW, UUID.randomUUID(), NOW, NOW, 1L
        );

        assertThatThrownBy(() -> rejectedProfile.updateLegalInfo(actor, createValidBusinessLegalInfo(), NOW))
                .isInstanceOf(DomainException.class)
                .satisfies(ex -> assertThat(((DomainException) ex).getErrorCode())
                        .isEqualTo(SellerErrorCode.INVALID_STATE));
    }

    @Test
    void updateLegalInfoWithEqualInfoReturnsFalseAndDoesNotChangeUpdatedAt() {
        SellerProfileId id = SellerProfileId.of(UUID.randomUUID());
        SellerActor actor = createActor(OWNER_ID, "USER");
        LegalInfo initialInfo = createValidIndividualLegalInfo();
        SellerProfile profile = SellerProfile.submit(id, actor, initialInfo, createValidShopInfo("Cửa Hàng A"), UUID.randomUUID(), NOW);

        boolean changed = profile.updateLegalInfo(actor, initialInfo, NOW.plusSeconds(3600));
        assertThat(changed).isFalse();
        assertThat(profile.updatedAt()).isEqualTo(NOW_MICROS);
    }

    @Test
    void updateLegalInfoValidationOrder() {
        SellerProfileId id = SellerProfileId.of(UUID.randomUUID());
        SellerProfile approvedProfile = SellerProfile.restore(
                id, OWNER_ID, SellerProfileStatus.APPROVED,
                createValidIndividualLegalInfo(), createValidShopInfo("Cửa Hàng B"),
                null, NOW, NOW, UUID.randomUUID(), NOW, NOW, 1L
        );

        SellerActor adminActor = createActor(OWNER_ID, "ADMIN");
        assertThatThrownBy(() -> approvedProfile.updateLegalInfo(adminActor, createValidBusinessLegalInfo(), NOW))
                .isInstanceOf(DomainException.class)
                .satisfies(ex -> assertThat(((DomainException) ex).getErrorCode())
                        .isEqualTo(SellerErrorCode.SELLER_ROLE_NOT_ALLOWED));

        SellerActor otherUser = createActor(OTHER_USER_ID, "USER");
        assertThatThrownBy(() -> approvedProfile.updateLegalInfo(otherUser, createValidBusinessLegalInfo(), NOW))
                .isInstanceOf(DomainException.class)
                .satisfies(ex -> assertThat(((DomainException) ex).getErrorCode())
                        .isEqualTo(SellerErrorCode.SELLER_PROFILE_NOT_FOUND));
    }

    // --- RESUBMIT TESTS ---

    @Test
    void resubmitSucceedsOnlyInRejectedAndClearsReviewFields() {
        SellerProfileId id = SellerProfileId.of(UUID.randomUUID());
        SellerActor actor = createActor(OWNER_ID, "USER");
        UUID reviewerId = UUID.randomUUID();
        Instant reviewedAt = NOW.plusSeconds(1800);
        Instant resubmitTime = NOW.plusSeconds(3600);
        Instant resubmitTimeMicros = resubmitTime.truncatedTo(java.time.temporal.ChronoUnit.MICROS);

        SellerProfile rejectedProfile = SellerProfile.restore(
                id, OWNER_ID, SellerProfileStatus.REJECTED,
                createValidIndividualLegalInfo(), createValidShopInfo("Cửa Hàng Cũ"),
                "Lý do từ chối", NOW, reviewedAt, reviewerId, NOW, NOW, 1L
        );

        LegalInfo newLegal = createValidBusinessLegalInfo();
        ShopInfo newShop = createValidShopInfo("Mới");

        rejectedProfile.resubmit(actor, newLegal, newShop, UUID.randomUUID(), resubmitTime);

        assertThat(rejectedProfile.status()).isEqualTo(SellerProfileStatus.PENDING_REVIEW);
        assertThat(rejectedProfile.rejectionReason()).isNull();
        assertThat(rejectedProfile.reviewedAt()).isNull();
        assertThat(rejectedProfile.reviewedBy()).isNull();
        assertThat(rejectedProfile.legalInfo()).isEqualTo(newLegal);
        assertThat(rejectedProfile.shopInfo()).isEqualTo(newShop);
        assertThat(rejectedProfile.submittedAt()).isEqualTo(resubmitTimeMicros);
        assertThat(rejectedProfile.updatedAt()).isEqualTo(resubmitTimeMicros);
        assertThat(rejectedProfile.createdAt()).isEqualTo(NOW);
    }

    @Test
    void resubmitEmitsExactlyOneSubmittedEventWithNewSubmittedAt() {
        SellerProfileId id = SellerProfileId.of(UUID.randomUUID());
        UUID eventId = UUID.randomUUID();
        SellerActor actor = createActor(OWNER_ID, "USER");
        Instant resubmitTime = NOW.plusSeconds(3600);
        SellerProfile rejected = SellerProfile.restore(
                id, OWNER_ID, SellerProfileStatus.REJECTED,
                createValidIndividualLegalInfo(), createValidShopInfo("Cửa Hàng Cũ"),
                "Lý do từ chối", NOW, NOW.plusSeconds(1800), UUID.randomUUID(), NOW, NOW, 1L);

        rejected.resubmit(actor, createValidBusinessLegalInfo(), createValidShopInfo("Mới"), eventId, resubmitTime);

        assertThat(rejected.getDomainEvents()).hasSize(1);
        SellerProfileSubmitted event = (SellerProfileSubmitted) rejected.getDomainEvents().iterator().next();
        assertThat(event.getEventId()).isEqualTo(eventId.toString());
        assertThat(event.accountId()).isEqualTo(OWNER_ID);
        assertThat(event.profileId()).isEqualTo(id);
        assertThat(event.submittedAt()).isEqualTo(rejected.submittedAt());
        assertThat(event.submittedAt()).isEqualTo(resubmitTime.truncatedTo(java.time.temporal.ChronoUnit.MICROS));
        assertThat(event.getOccurredOn()).isEqualTo(resubmitTime);
    }

    @Test
    void failedResubmitEmitsNoEvent() {
        SellerActor actor = createActor(OWNER_ID, "USER");
        SellerProfile pending = SellerProfile.restore(
                SellerProfileId.of(UUID.randomUUID()), OWNER_ID, SellerProfileStatus.PENDING_REVIEW,
                createValidIndividualLegalInfo(), createValidShopInfo("Cửa Hàng Xanh"),
                null, NOW, null, null, NOW, NOW, 1L);

        assertThatThrownBy(() -> pending.resubmit(
                actor, createValidIndividualLegalInfo(), createValidShopInfo("Mới"), UUID.randomUUID(), NOW))
                .isInstanceOf(DomainException.class);

        // Nộp lại bị từ chối (sai trạng thái) thì không được để lại sự kiện nào chờ phát
        assertThat(pending.getDomainEvents()).isEmpty();
    }

    @Test
    void resubmitRejectsNullEventId() {
        SellerProfile rejected = SellerProfile.restore(
                SellerProfileId.of(UUID.randomUUID()), OWNER_ID, SellerProfileStatus.REJECTED,
                createValidIndividualLegalInfo(), createValidShopInfo("Cửa Hàng Cũ"),
                "Lý do", NOW, NOW, UUID.randomUUID(), NOW, NOW, 1L);

        assertThatThrownBy(() -> rejected.resubmit(
                createActor(OWNER_ID, "USER"), createValidIndividualLegalInfo(), createValidShopInfo("Mới"), null, NOW))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void resubmitThrowsInPendingReviewAndApproved() {
        SellerProfileId id = SellerProfileId.of(UUID.randomUUID());
        SellerActor actor = createActor(OWNER_ID, "USER");

        SellerProfile pendingProfile = SellerProfile.submit(id, actor, createValidIndividualLegalInfo(), createValidShopInfo("Cửa Hàng A1"), UUID.randomUUID(), NOW);
        assertThatThrownBy(() -> pendingProfile.resubmit(actor, createValidIndividualLegalInfo(), createValidShopInfo("Cửa Hàng B1"), UUID.randomUUID(), NOW))
                .isInstanceOf(DomainException.class)
                .satisfies(ex -> assertThat(((DomainException) ex).getErrorCode())
                        .isEqualTo(SellerErrorCode.INVALID_STATE));

        SellerProfile approvedProfile = SellerProfile.restore(
                id, OWNER_ID, SellerProfileStatus.APPROVED,
                createValidIndividualLegalInfo(), createValidShopInfo("Cửa Hàng A1"),
                null, NOW, NOW, UUID.randomUUID(), NOW, NOW, 1L
        );
        assertThatThrownBy(() -> approvedProfile.resubmit(actor, createValidIndividualLegalInfo(), createValidShopInfo("Cửa Hàng B1"), UUID.randomUUID(), NOW))
                .isInstanceOf(DomainException.class)
                .satisfies(ex -> assertThat(((DomainException) ex).getErrorCode())
                        .isEqualTo(SellerErrorCode.INVALID_STATE));
    }

    @Test
    void resubmitValidationOrder() {
        SellerProfileId id = SellerProfileId.of(UUID.randomUUID());
        SellerProfile rejectedProfile = SellerProfile.restore(
                id, OWNER_ID, SellerProfileStatus.REJECTED,
                createValidIndividualLegalInfo(), createValidShopInfo("Cửa Hàng A1"),
                "Từ chối", NOW, NOW, UUID.randomUUID(), NOW, NOW, 1L
        );

        SellerActor adminActor = createActor(OWNER_ID, "ADMIN");
        assertThatThrownBy(() -> rejectedProfile.resubmit(adminActor, createValidIndividualLegalInfo(), createValidShopInfo("Cửa Hàng B1"), UUID.randomUUID(), NOW))
                .isInstanceOf(DomainException.class)
                .satisfies(ex -> assertThat(((DomainException) ex).getErrorCode())
                        .isEqualTo(SellerErrorCode.SELLER_ROLE_NOT_ALLOWED));

        SellerActor otherUser = createActor(OTHER_USER_ID, "USER");
        assertThatThrownBy(() -> rejectedProfile.resubmit(otherUser, createValidIndividualLegalInfo(), createValidShopInfo("Cửa Hàng B1"), UUID.randomUUID(), NOW))
                .isInstanceOf(DomainException.class)
                .satisfies(ex -> assertThat(((DomainException) ex).getErrorCode())
                        .isEqualTo(SellerErrorCode.SELLER_PROFILE_NOT_FOUND));
    }

    // --- RESTORE TESTS ---

    @Test
    void restorePreservesExactTimestampsWithoutMicrosTruncation() {
        SellerProfileId id = SellerProfileId.of(UUID.randomUUID());
        Instant exactTimeWithNanos = Instant.parse("2026-10-26T03:04:05.123456789Z");
        UUID reviewerId = UUID.randomUUID();

        SellerProfile profile = SellerProfile.restore(
                id, OWNER_ID, SellerProfileStatus.APPROVED,
                createValidIndividualLegalInfo(), createValidShopInfo("Cửa Hàng"),
                "rejection", exactTimeWithNanos, exactTimeWithNanos, reviewerId,
                exactTimeWithNanos, exactTimeWithNanos, 1L
        );

        assertThat(profile.submittedAt()).isEqualTo(exactTimeWithNanos);
        assertThat(profile.reviewedAt()).isEqualTo(exactTimeWithNanos);
        assertThat(profile.createdAt()).isEqualTo(exactTimeWithNanos);
        assertThat(profile.updatedAt()).isEqualTo(exactTimeWithNanos);
    }

    @Test
    void restoreRejectsNullRequiredParameters() {
        SellerProfileId id = SellerProfileId.of(UUID.randomUUID());
        LegalInfo legalInfo = createValidIndividualLegalInfo();
        ShopInfo shopInfo = createValidShopInfo("Cửa Hàng");

        assertThatThrownBy(() -> SellerProfile.restore(null, OWNER_ID, SellerProfileStatus.APPROVED, legalInfo, shopInfo, null, NOW, null, null, NOW, NOW, 1L))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> SellerProfile.restore(id, null, SellerProfileStatus.APPROVED, legalInfo, shopInfo, null, NOW, null, null, NOW, NOW, 1L))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> SellerProfile.restore(id, OWNER_ID, null, legalInfo, shopInfo, null, NOW, null, null, NOW, NOW, 1L))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> SellerProfile.restore(id, OWNER_ID, SellerProfileStatus.APPROVED, null, shopInfo, null, NOW, null, null, NOW, NOW, 1L))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> SellerProfile.restore(id, OWNER_ID, SellerProfileStatus.APPROVED, legalInfo, null, null, NOW, null, null, NOW, NOW, 1L))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> SellerProfile.restore(id, OWNER_ID, SellerProfileStatus.APPROVED, legalInfo, shopInfo, null, null, null, null, NOW, NOW, 1L))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> SellerProfile.restore(id, OWNER_ID, SellerProfileStatus.APPROVED, legalInfo, shopInfo, null, NOW, null, null, null, NOW, 1L))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> SellerProfile.restore(id, OWNER_ID, SellerProfileStatus.APPROVED, legalInfo, shopInfo, null, NOW, null, null, NOW, null, 1L))
                .isInstanceOf(NullPointerException.class);
    }
}
