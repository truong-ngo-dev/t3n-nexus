package vn.t3nexus.inventory.domain.limitedoffer;

import vn.t3nexus.lib.common.domain.model.AbstractAggregateRoot;
import vn.t3nexus.lib.common.domain.model.AggregateRoot;

import java.time.Instant;

public class LimitedOffer extends AbstractAggregateRoot<LimitedOfferId> implements AggregateRoot<LimitedOfferId> {

    private final String skuId;
    private int maxQuantity;
    private int windowSeconds;
    private LimitedOfferStatus status;
    private Instant activatedAt;
    private Instant endedAt;
    private final Instant createdAt;
    private Instant updatedAt;

    private LimitedOffer(LimitedOfferId id, String skuId, int maxQuantity, int windowSeconds,
                         LimitedOfferStatus status, Instant activatedAt, Instant endedAt,
                         Instant createdAt, Instant updatedAt) {
        setId(id);
        this.skuId         = skuId;
        this.maxQuantity   = maxQuantity;
        this.windowSeconds = windowSeconds;
        this.status        = status;
        this.activatedAt   = activatedAt;
        this.endedAt       = endedAt;
        this.createdAt     = createdAt;
        this.updatedAt     = updatedAt;
    }

    // ───────────── Factory Methods ─────────────

    public static LimitedOffer create(LimitedOfferId id, String skuId, int maxQuantity, int windowSeconds) {
        Instant now = Instant.now();
        return new LimitedOffer(id, skuId, maxQuantity, windowSeconds,
                LimitedOfferStatus.INACTIVE, null, null, now, now);
    }

    public static LimitedOffer reconstitute(LimitedOfferId id, String skuId, int maxQuantity, int windowSeconds,
                                            LimitedOfferStatus status, Instant activatedAt, Instant endedAt,
                                            Instant createdAt, Instant updatedAt) {
        return new LimitedOffer(id, skuId, maxQuantity, windowSeconds, status, activatedAt, endedAt, createdAt, updatedAt);
    }

    // ───────────── Behaviour ─────────────

    public void activate(int maxQuantity, int windowSeconds) {
        if (status == LimitedOfferStatus.ACTIVE) throw LimitedOfferException.alreadyActive();
        this.maxQuantity   = maxQuantity;
        this.windowSeconds = windowSeconds;
        this.status        = LimitedOfferStatus.ACTIVE;
        this.activatedAt   = Instant.now();
        this.updatedAt     = Instant.now();
    }

    public void end() {
        if (status != LimitedOfferStatus.ACTIVE) throw LimitedOfferException.notActive();
        this.status    = LimitedOfferStatus.ENDED;
        this.endedAt   = Instant.now();
        this.updatedAt = Instant.now();
    }

    // ───────────── Getters ─────────────

    public String getSkuId()             { return skuId; }
    public int getMaxQuantity()          { return maxQuantity; }
    public int getWindowSeconds()        { return windowSeconds; }
    public LimitedOfferStatus getStatus(){ return status; }
    public Instant getActivatedAt()      { return activatedAt; }
    public Instant getEndedAt()          { return endedAt; }
    public Instant getCreatedAt()        { return createdAt; }
    public Instant getUpdatedAt()        { return updatedAt; }

    public boolean isActive() { return status == LimitedOfferStatus.ACTIVE; }
}
