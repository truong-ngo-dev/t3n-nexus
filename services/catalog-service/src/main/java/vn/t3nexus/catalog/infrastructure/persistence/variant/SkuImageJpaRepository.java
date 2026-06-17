package vn.t3nexus.catalog.infrastructure.persistence.variant;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface SkuImageJpaRepository extends JpaRepository<SkuImageJpaEntity, String> {

    List<SkuImageJpaEntity> findByVariantId(String variantId);

    List<SkuImageJpaEntity> findByVariantIdIn(List<String> variantIds);

    @Transactional
    void deleteByVariantId(String variantId);
}
