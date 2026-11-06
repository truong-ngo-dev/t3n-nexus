package vn.t3nexus.seller.domain.sellerprofile;

import vn.t3nexus.lib.common.domain.model.AbstractDomainEvent;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Sự kiện miền phát ra khi hồ sơ người bán được duyệt bởi quản trị viên.
 */
public final class SellerProfileApproved extends AbstractDomainEvent {

    private final UUID accountId;
    private final SellerProfileId profileId;
    private final long version;
    private final UUID reviewedBy;
    private final long viewedVersion;

    public SellerProfileApproved(
            UUID eventId,
            Instant occurredOn,
            UUID accountId,
            SellerProfileId profileId,
            long version,
            UUID reviewedBy,
            long viewedVersion
    ) {
        super(
                Objects.requireNonNull(eventId, "eventId").toString(),
                Objects.requireNonNull(occurredOn, "occurredOn"),
                Objects.requireNonNull(accountId, "accountId").toString(),
                "SellerProfile"
        );
        this.accountId = accountId;
        this.profileId = Objects.requireNonNull(profileId, "profileId");
        this.version = version;
        this.reviewedBy = Objects.requireNonNull(reviewedBy, "reviewedBy");
        this.viewedVersion = viewedVersion;
    }

    public UUID accountId() {
        return accountId;
    }

    public SellerProfileId profileId() {
        return profileId;
    }

    public long version() {
        return version;
    }

    public UUID reviewedBy() {
        return reviewedBy;
    }

    public long viewedVersion() {
        return viewedVersion;
    }

    @Override
    public String getRoutingKey() {
        return "seller.profile.approved";
    }

    @Override
    public Object getPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("accountId", accountId.toString());
        payload.put("profileId", profileId.getValue().toString());
        payload.put("version", Long.valueOf(version));
        payload.put("occurredAt", getOccurredOn().toString());
        return Collections.unmodifiableMap(payload);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[eventId=" + getEventId() + ", profileId=" + profileId.getValue() + "]";
    }
}
