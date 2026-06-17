package vn.t3nexus.catalog.infrastructure.persistence.product;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface ProductImageJpaRepository extends JpaRepository<ProductImageJpaEntity, String> {

    List<ProductImageJpaEntity> findByProductId(String productId);

    List<ProductImageJpaEntity> findByProductIdIn(List<String> productIds);

    @Transactional
    void deleteByProductId(String productId);
}
