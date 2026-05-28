package vn.t3nexus.customer.domain.customer;

import vn.t3nexus.lib.common.domain.model.AbstractAggregateRoot;
import vn.t3nexus.lib.common.domain.model.AggregateRoot;

import java.time.Instant;

public class CustomerProfile extends AbstractAggregateRoot<CustomerProfileId> implements AggregateRoot<CustomerProfileId> {

    private final String userId;   // cross-BC ref — String primitive, no FK
    private final Instant createdAt;
    private Instant updatedAt;

    private CustomerProfile(CustomerProfileId id, String userId, Instant createdAt, Instant updatedAt) {
        setId(id);
        this.userId    = userId;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    // ───────────── Factory Methods ─────────────

    public static CustomerProfile create(CustomerProfileId id, String userId) {
        Instant now = Instant.now();
        return new CustomerProfile(id, userId, now, now);
    }

    public static CustomerProfile reconstitute(CustomerProfileId id, String userId,
                                               Instant createdAt, Instant updatedAt) {
        return new CustomerProfile(id, userId, createdAt, updatedAt);
    }

    // ───────────── Getters ─────────────

    public String getUserId()      { return userId; }
    public Instant getCreatedAt()  { return createdAt; }
    public Instant getUpdatedAt()  { return updatedAt; }
}
