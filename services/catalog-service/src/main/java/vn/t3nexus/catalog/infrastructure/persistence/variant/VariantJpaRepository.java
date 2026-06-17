package vn.t3nexus.catalog.infrastructure.persistence.variant;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.t3nexus.catalog.domain.variant.VariantStatus;

import java.util.List;

public interface VariantJpaRepository extends JpaRepository<VariantJpaEntity, String> {

    List<VariantJpaEntity> findByProductId(String productId);

    boolean existsByProductId(String productId);

    boolean existsByProductIdAndStatus(String productId, VariantStatus status);

    boolean existsByProductIdAndCombinationHash(String productId, String combinationHash);
}
