package vn.t3nexus.customer.domain.customer;

import vn.t3nexus.lib.common.domain.model.AbstractAggregateRoot;
import vn.t3nexus.lib.common.domain.model.AggregateRoot;

import java.time.Instant;

/**
 * Quan hệ identifying 1-1 với {@code UserAccount} (identity-service) — {@link CustomerProfileId}
 * dùng thẳng giá trị {@code userId}, không tự sinh ULID riêng. Không có "userId" như 1 field
 * riêng: {@code getId().getValue()} chính là userId, tránh 2 field cùng mang 1 giá trị.
 */
public class CustomerProfile extends AbstractAggregateRoot<CustomerProfileId> implements AggregateRoot<CustomerProfileId> {

    private final Instant createdAt;
    private Instant updatedAt;

    private CustomerProfile(CustomerProfileId id, Instant createdAt, Instant updatedAt) {
        setId(id);
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    // ───────────── Factory Methods ─────────────

    public static CustomerProfile create(CustomerProfileId id) {
        Instant now = Instant.now();
        return new CustomerProfile(id, now, now);
    }

    public static CustomerProfile reconstitute(CustomerProfileId id, Instant createdAt, Instant updatedAt) {
        return new CustomerProfile(id, createdAt, updatedAt);
    }

    // ───────────── Getters ─────────────

    public Instant getCreatedAt()  { return createdAt; }
    public Instant getUpdatedAt()  { return updatedAt; }
}
