package vn.t3nexus.catalog.infrastructure.persistence.variant;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface VariantCombinationItemJpaRepository
        extends JpaRepository<VariantCombinationItemJpaEntity, VariantCombinationItemKey> {

    List<VariantCombinationItemJpaEntity> findByVariantIdInOrderByTemplateId(Collection<UUID> variantIds);
}
