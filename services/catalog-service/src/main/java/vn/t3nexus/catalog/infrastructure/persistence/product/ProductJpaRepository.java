package vn.t3nexus.catalog.infrastructure.persistence.product;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductJpaRepository extends JpaRepository<ProductJpaEntity, String> {

    List<ProductJpaEntity> findBySellerIdOrderByCreatedAtDesc(String sellerId, Pageable pageable);

    long countBySellerId(String sellerId);

    boolean existsByCategoryId(String categoryId);

    boolean existsByBrandId(String brandId);

    // Khoá dòng product tới hết transaction — serialize các lần ghi làm đổi quan hệ Product ↔ Variant (C-07).
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM ProductJpaEntity p WHERE p.id = :id")
    Optional<ProductJpaEntity> findByIdForUpdate(@Param("id") String id);

    /** Keyset pagination theo id — dùng cho replay snapshot, chỉ product đã từng publish. */
    @SuppressWarnings("all")
    @Query(value = """
            SELECT id FROM product
            WHERE published_at IS NOT NULL AND id > :afterId
            ORDER BY id
            LIMIT :size
            """, nativeQuery = true)
    List<String> findPublishedIdsAfter(@Param("afterId") String afterId, @Param("size") int size);
}
