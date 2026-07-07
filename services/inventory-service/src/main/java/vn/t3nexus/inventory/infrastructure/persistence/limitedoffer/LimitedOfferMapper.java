package vn.t3nexus.inventory.infrastructure.persistence.limitedoffer;

import vn.t3nexus.inventory.domain.limitedoffer.LimitedOffer;
import vn.t3nexus.inventory.domain.limitedoffer.LimitedOfferId;

public final class LimitedOfferMapper {

    private LimitedOfferMapper() {}

    public static LimitedOffer toDomain(LimitedOfferJpaEntity e) {
        return LimitedOffer.reconstitute(
                LimitedOfferId.of(e.getId()),
                e.getSkuId(),
                e.getMaxQuantity(),
                e.getWindowSeconds(),
                e.getStatus(),
                e.getActivatedAt(),
                e.getEndedAt(),
                e.getCreatedAt(),
                e.getUpdatedAt()
        );
    }

    public static LimitedOfferJpaEntity toJpaEntity(LimitedOffer domain) {
        LimitedOfferJpaEntity e = new LimitedOfferJpaEntity();
        e.setId(domain.getId().getValue());
        e.setSkuId(domain.getSkuId());
        e.setMaxQuantity(domain.getMaxQuantity());
        e.setWindowSeconds(domain.getWindowSeconds());
        e.setStatus(domain.getStatus());
        e.setActivatedAt(domain.getActivatedAt());
        e.setEndedAt(domain.getEndedAt());
        e.setCreatedAt(domain.getCreatedAt());
        e.setUpdatedAt(domain.getUpdatedAt());
        return e;
    }
}
