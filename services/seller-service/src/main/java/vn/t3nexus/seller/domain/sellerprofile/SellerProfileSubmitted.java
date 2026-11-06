package vn.t3nexus.seller.domain.sellerprofile;

import vn.t3nexus.lib.common.domain.model.AbstractDomainEvent;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Sự kiện miền phát ra khi hồ sơ người bán được nộp lần đầu hoặc nộp lại.
 */
public final class SellerProfileSubmitted extends AbstractDomainEvent {

    private final UUID accountId;
    private final SellerProfileId profileId;
    private final Instant submittedAt;

    public SellerProfileSubmitted(
            UUID eventId,
            Instant occurredOn,
            UUID accountId,
            SellerProfileId profileId,
            Instant submittedAt
    ) {
        super(
                Objects.requireNonNull(eventId, "eventId").toString(),
                Objects.requireNonNull(occurredOn, "occurredOn"),
                Objects.requireNonNull(accountId, "accountId").toString(),
                "SellerProfile"
        );
        this.accountId = accountId;
        this.profileId = Objects.requireNonNull(profileId, "profileId");
        this.submittedAt = Objects.requireNonNull(submittedAt, "submittedAt");
    }

    public UUID accountId() {
        return accountId;
    }

    public SellerProfileId profileId() {
        return profileId;
    }

    public Instant submittedAt() {
        return submittedAt;
    }

    @Override
    public String getRoutingKey() {
        return "seller.profile.submitted";
    }

    @Override
    public Object getPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("accountId", accountId.toString());
        payload.put("profileId", profileId.getValue().toString());
        payload.put("submittedAt", submittedAt.toString());
        return Collections.unmodifiableMap(payload);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[eventId=" + getEventId() + ", profileId=" + profileId.getValue() + "]";
    }
}
