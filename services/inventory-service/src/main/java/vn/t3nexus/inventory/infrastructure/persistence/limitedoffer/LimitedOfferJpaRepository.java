package vn.t3nexus.inventory.infrastructure.persistence.limitedoffer;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.t3nexus.inventory.domain.limitedoffer.LimitedOfferStatus;

import java.util.Optional;

@Repository
public interface LimitedOfferJpaRepository extends JpaRepository<LimitedOfferJpaEntity, String> {

    Optional<LimitedOfferJpaEntity> findBySkuId(String skuId);

    boolean existsBySkuIdAndStatus(String skuId, LimitedOfferStatus status);
}
