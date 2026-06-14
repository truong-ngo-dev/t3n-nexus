package vn.t3nexus.catalog.domain.brand;

import vn.t3nexus.lib.common.domain.model.AbstractAggregateRoot;
import vn.t3nexus.lib.common.domain.model.AggregateRoot;

import java.time.Instant;

public class Brand extends AbstractAggregateRoot<BrandId> implements AggregateRoot<BrandId> {

    private String name;
    private final String slug;
    private BrandStatus status;
    private final Instant createdAt;
    private Instant updatedAt;

    private Brand(BrandId id, String name, String slug, BrandStatus status,
                  Instant createdAt, Instant updatedAt) {
        setId(id);
        this.name      = name;
        this.slug      = slug;
        this.status    = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    // ───────────── Factory Methods ─────────────

    public static Brand create(BrandId id, String name, String slug) {
        Instant now = Instant.now();
        return new Brand(id, name, slug, BrandStatus.ACTIVE, now, now);
    }

    public static Brand reconstitute(BrandId id, String name, String slug, BrandStatus status,
                                     Instant createdAt, Instant updatedAt) {
        return new Brand(id, name, slug, status, createdAt, updatedAt);
    }

    // ───────────── Behaviour ─────────────

    public void update(String name) {
        this.name      = name;
        this.updatedAt = Instant.now();
    }

    public void deactivate() {
        this.status    = BrandStatus.INACTIVE;
        this.updatedAt = Instant.now();
    }

    // ───────────── Getters ─────────────

    public String getName()        { return name; }
    public String getSlug()        { return slug; }
    public BrandStatus getStatus() { return status; }
    public Instant getCreatedAt()  { return createdAt; }
    public Instant getUpdatedAt()  { return updatedAt; }
}
