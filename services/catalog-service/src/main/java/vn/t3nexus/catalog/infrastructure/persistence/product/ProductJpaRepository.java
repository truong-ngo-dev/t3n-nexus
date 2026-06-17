package vn.t3nexus.catalog.infrastructure.persistence.product;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductJpaRepository extends JpaRepository<ProductJpaEntity, String> {

    List<ProductJpaEntity> findBySellerIdOrderByCreatedAtDesc(String sellerId, Pageable pageable);

    long countBySellerId(String sellerId);

    boolean existsByCategoryId(String categoryId);

    boolean existsByBrandId(String brandId);
}
