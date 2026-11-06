package vn.t3nexus.seller.domain.sellerprofile;

import vn.t3nexus.lib.common.domain.exception.DomainException;
import vn.t3nexus.lib.common.domain.model.AbstractAggregateRoot;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

/**
 * Gốc tập hợp hồ sơ người bán.
 */
public final class SellerProfile extends AbstractAggregateRoot<SellerProfileId> {

    private static final int MAX_REASON_LENGTH = 500;

    private final UUID ownerAccountId;
    private SellerProfileStatus status;
    private LegalInfo legalInfo;
    private ShopInfo shopInfo;
    private String rejectionReason;
    private Instant submittedAt;
    private Instant reviewedAt;
    private UUID reviewedBy;
    private final Instant createdAt;
    private Instant updatedAt;
    private final Long version;

    private SellerProfile(
            SellerProfileId id,
            UUID ownerAccountId,
            SellerProfileStatus status,
            LegalInfo legalInfo,
            ShopInfo shopInfo,
            String rejectionReason,
            Instant submittedAt,
            Instant reviewedAt,
            UUID reviewedBy,
            Instant createdAt,
            Instant updatedAt,
            Long version
    ) {
        super.setId(id);
        this.ownerAccountId = ownerAccountId;
        this.status = status;
        this.legalInfo = legalInfo;
        this.shopInfo = shopInfo;
        this.rejectionReason = rejectionReason;
        this.submittedAt = submittedAt;
        this.reviewedAt = reviewedAt;
        this.reviewedBy = reviewedBy;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.version = version;
    }

    @Override
    public void setId(SellerProfileId id) {
        throw new UnsupportedOperationException("Hồ sơ người bán không đổi mã");
    }

    public static SellerProfile submit(
            SellerProfileId id,
            SellerActor actor,
            LegalInfo legalInfo,
            ShopInfo shopInfo,
            Instant now
    ) {
        Objects.requireNonNull(actor, "actor");
        actor.requireUser();

        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(legalInfo, "legalInfo");
        Objects.requireNonNull(shopInfo, "shopInfo");
        Objects.requireNonNull(now, "now");

        Instant truncatedNow = now.truncatedTo(ChronoUnit.MICROS);

        return new SellerProfile(
                id,
                actor.accountId(),
                SellerProfileStatus.PENDING_REVIEW,
                legalInfo,
                shopInfo,
                null,
                truncatedNow,
                null,
                null,
                truncatedNow,
                truncatedNow,
                null
        );
    }

    public static SellerProfile restore(
            SellerProfileId id,
            UUID ownerAccountId,
            SellerProfileStatus status,
            LegalInfo legalInfo,
            ShopInfo shopInfo,
            String rejectionReason,
            Instant submittedAt,
            Instant reviewedAt,
            UUID reviewedBy,
            Instant createdAt,
            Instant updatedAt,
            Long version
    ) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(ownerAccountId, "ownerAccountId");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(legalInfo, "legalInfo");
        Objects.requireNonNull(shopInfo, "shopInfo");
        Objects.requireNonNull(submittedAt, "submittedAt");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");

        return new SellerProfile(
                id,
                ownerAccountId,
                status,
                legalInfo,
                shopInfo,
                rejectionReason,
                submittedAt,
                reviewedAt,
                reviewedBy,
                createdAt,
                updatedAt,
                version
        );
    }

    public void approve(SellerActor actor, long viewedVersion, UUID eventId, Instant now) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(now, "now");

        actor.requireAdmin();

        if (status != SellerProfileStatus.PENDING_REVIEW) {
            throw new DomainException(SellerErrorCode.INVALID_STATE);
        }

        if (version == null) {
            throw new IllegalStateException("Hồ sơ chưa được lưu, không có phiên bản");
        }

        if (viewedVersion != version) {
            throw new DomainException(SellerErrorCode.STALE_VERSION);
        }

        Instant truncated = now.truncatedTo(ChronoUnit.MICROS);
        this.status = SellerProfileStatus.APPROVED;
        this.reviewedAt = truncated;
        this.reviewedBy = actor.accountId();
        this.updatedAt = truncated;

        addDomainEvent(new SellerProfileApproved(
                eventId,
                now,
                ownerAccountId,
                getId(),
                version + 1,
                actor.accountId(),
                viewedVersion
        ));
    }

    public void reject(SellerActor actor, long viewedVersion, String reason, UUID eventId, Instant now) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(now, "now");

        actor.requireAdmin();

        if (reason == null || reason.isBlank()) {
            throw new SellerFieldException(SellerErrorCode.REJECTION_REASON_REQUIRED, "reason");
        }

        String trimmed = reason.strip();
        if (trimmed.length() > MAX_REASON_LENGTH) {
            throw new SellerFieldException(SellerErrorCode.SELLER_FIELD_INVALID, "reason");
        }

        if (status != SellerProfileStatus.PENDING_REVIEW) {
            throw new DomainException(SellerErrorCode.INVALID_STATE);
        }

        if (version == null) {
            throw new IllegalStateException("Hồ sơ chưa được lưu, không có phiên bản");
        }

        if (viewedVersion != version) {
            throw new DomainException(SellerErrorCode.STALE_VERSION);
        }

        Instant truncated = now.truncatedTo(ChronoUnit.MICROS);
        this.status = SellerProfileStatus.REJECTED;
        this.rejectionReason = trimmed;
        this.reviewedAt = truncated;
        this.reviewedBy = actor.accountId();
        this.updatedAt = truncated;

        addDomainEvent(new SellerProfileRejected(
                eventId,
                now,
                ownerAccountId,
                getId(),
                version + 1,
                actor.accountId(),
                viewedVersion,
                trimmed
        ));
    }

    public boolean updateShopInfo(SellerActor actor, ShopInfo newInfo, Instant now) {
        actor.requireUser();
        if (!actor.accountId().equals(ownerAccountId)) {
            throw new DomainException(SellerErrorCode.SELLER_PROFILE_NOT_FOUND);
        }
        if (status == SellerProfileStatus.REJECTED) {
            throw new DomainException(SellerErrorCode.INVALID_STATE);
        }

        if (newInfo.equals(this.shopInfo)) {
            return false;
        }

        this.shopInfo = newInfo;
        this.updatedAt = now.truncatedTo(ChronoUnit.MICROS);
        return true;
    }

    public boolean updateLegalInfo(SellerActor actor, LegalInfo newInfo, Instant now) {
        actor.requireUser();
        if (!actor.accountId().equals(ownerAccountId)) {
            throw new DomainException(SellerErrorCode.SELLER_PROFILE_NOT_FOUND);
        }
        if (status == SellerProfileStatus.APPROVED) {
            throw new DomainException(SellerErrorCode.LEGAL_INFO_LOCKED);
        }
        if (status == SellerProfileStatus.REJECTED) {
            throw new DomainException(SellerErrorCode.INVALID_STATE);
        }

        if (newInfo.equals(this.legalInfo)) {
            return false;
        }

        this.legalInfo = newInfo;
        this.updatedAt = now.truncatedTo(ChronoUnit.MICROS);
        return true;
    }

    public void resubmit(SellerActor actor, LegalInfo newLegalInfo, ShopInfo newShopInfo, Instant now) {
        actor.requireUser();
        if (!actor.accountId().equals(ownerAccountId)) {
            throw new DomainException(SellerErrorCode.SELLER_PROFILE_NOT_FOUND);
        }
        if (status != SellerProfileStatus.REJECTED) {
            throw new DomainException(SellerErrorCode.INVALID_STATE);
        }

        Instant truncatedNow = now.truncatedTo(ChronoUnit.MICROS);
        this.legalInfo = newLegalInfo;
        this.shopInfo = newShopInfo;
        this.status = SellerProfileStatus.PENDING_REVIEW;
        this.rejectionReason = null;
        this.reviewedAt = null;
        this.reviewedBy = null;
        this.submittedAt = truncatedNow;
        this.updatedAt = truncatedNow;
    }

    public Long version() {
        return version;
    }

    public UUID ownerAccountId() {
        return ownerAccountId;
    }

    public SellerProfileStatus status() {
        return status;
    }

    public SellerType sellerType() {
        return legalInfo.type();
    }

    public LegalInfo legalInfo() {
        return legalInfo;
    }

    public ShopInfo shopInfo() {
        return shopInfo;
    }

    public String rejectionReason() {
        return rejectionReason;
    }

    public Instant submittedAt() {
        return submittedAt;
    }

    public Instant reviewedAt() {
        return reviewedAt;
    }

    public UUID reviewedBy() {
        return reviewedBy;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}
