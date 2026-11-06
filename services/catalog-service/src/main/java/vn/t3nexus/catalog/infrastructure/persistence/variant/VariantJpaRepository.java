package vn.t3nexus.catalog.infrastructure.persistence.variant;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface VariantJpaRepository extends JpaRepository<VariantJpaEntity, UUID> {

    Optional<VariantJpaEntity> findByIdAndProductId(UUID id, UUID productId);

    List<VariantJpaEntity> findByProductIdOrderByCreatedAtAscIdAsc(UUID productId);

    long countByProductId(UUID productId);

    boolean existsByProductIdAndCombinationKey(UUID productId, String combinationKey);

    boolean existsBySellerIdAndSku(UUID sellerId, String sku);

    boolean existsBySellerIdAndSkuAndIdNot(UUID sellerId, String sku, UUID id);

    /** Xóa ngay bằng một câu lệnh; tổ hợp xóa theo khóa ngoại CASCADE. */
    @org.springframework.data.jpa.repository.Modifying(flushAutomatically = true, clearAutomatically = true)
    @org.springframework.data.jpa.repository.Query("DELETE FROM VariantJpaEntity v WHERE v.productId = :productId")
    void deleteByProductId(@org.springframework.data.repository.query.Param("productId") java.util.UUID productId);
}
