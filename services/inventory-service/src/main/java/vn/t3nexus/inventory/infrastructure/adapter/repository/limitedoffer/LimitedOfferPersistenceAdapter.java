package vn.t3nexus.inventory.infrastructure.adapter.repository.limitedoffer;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.t3nexus.inventory.domain.limitedoffer.LimitedOffer;
import vn.t3nexus.inventory.domain.limitedoffer.LimitedOfferId;
import vn.t3nexus.inventory.domain.limitedoffer.LimitedOfferRepository;
import vn.t3nexus.inventory.domain.limitedoffer.LimitedOfferStatus;
import vn.t3nexus.inventory.infrastructure.persistence.limitedoffer.LimitedOfferJpaRepository;
import vn.t3nexus.inventory.infrastructure.persistence.limitedoffer.LimitedOfferMapper;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class LimitedOfferPersistenceAdapter implements LimitedOfferRepository {

    private final LimitedOfferJpaRepository jpaRepository;

    @Override
    public Optional<LimitedOffer> findById(LimitedOfferId id) {
        return jpaRepository.findById(id.getValue()).map(LimitedOfferMapper::toDomain);
    }

    @Override
    public Optional<LimitedOffer> findBySkuId(String skuId) {
        return jpaRepository.findBySkuId(skuId).map(LimitedOfferMapper::toDomain);
    }

    @Override
    public boolean existsBySkuIdAndStatus(String skuId, LimitedOfferStatus status) {
        return jpaRepository.existsBySkuIdAndStatus(skuId, status);
    }

    @Override
    public void save(LimitedOffer aggregate) {
        jpaRepository.save(LimitedOfferMapper.toJpaEntity(aggregate));
    }

    @Override
    public void delete(LimitedOfferId id) {
        jpaRepository.deleteById(id.getValue());
    }
}
