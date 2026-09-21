package vn.t3nexus.catalog.infrastructure.persistence.product;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface ProductVariantDefiningAttributeJpaRepository
        extends JpaRepository<ProductVariantDefiningAttributeJpaEntity, ProductVariantDefiningAttributeKey> {

    List<ProductVariantDefiningAttributeJpaEntity> findByProductId(String productId);

    List<ProductVariantDefiningAttributeJpaEntity> findByProductIdIn(List<String> productIds);

    @Transactional
    void deleteByProductId(String productId);
}
