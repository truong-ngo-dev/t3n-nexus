package vn.t3nexus.catalog.infrastructure.persistence.product;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface ProductAttributeValueJpaRepository
        extends JpaRepository<ProductAttributeValueJpaEntity, ProductAttributeValueKey> {

    List<ProductAttributeValueJpaEntity> findByProductId(String productId);

    List<ProductAttributeValueJpaEntity> findByProductIdIn(List<String> productIds);

    @Transactional
    void deleteByProductId(String productId);
}
