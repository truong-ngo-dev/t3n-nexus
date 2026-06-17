package vn.t3nexus.catalog.infrastructure.persistence.variant;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface VariantCombinationItemJpaRepository
        extends JpaRepository<VariantCombinationItemJpaEntity, VariantCombinationItemKey> {

    boolean existsByOptionId(String optionId);

    List<VariantCombinationItemJpaEntity> findByVariantId(String variantId);

    List<VariantCombinationItemJpaEntity> findByVariantIdIn(List<String> variantIds);

    @Transactional
    void deleteByVariantId(String variantId);
}
