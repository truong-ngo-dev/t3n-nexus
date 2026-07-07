package vn.t3nexus.inventory.domain.limitedoffer;

import vn.t3nexus.lib.common.domain.service.Repository;

import java.util.Optional;

public interface LimitedOfferRepository extends Repository<LimitedOffer, LimitedOfferId> {

    Optional<LimitedOffer> findBySkuId(String skuId);

    boolean existsBySkuIdAndStatus(String skuId, LimitedOfferStatus status);
}
