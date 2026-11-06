package vn.t3nexus.catalog.infrastructure.persistence.product;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ProductAttributeOptionJpaRepository
        extends JpaRepository<ProductAttributeOptionJpaEntity, ProductAttributeOptionKey> {

    List<ProductAttributeOptionJpaEntity> findByProductIdOrderByTemplateIdAscOptionIdAsc(UUID productId);
}
