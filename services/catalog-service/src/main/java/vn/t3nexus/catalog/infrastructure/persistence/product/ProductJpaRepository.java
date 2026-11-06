package vn.t3nexus.catalog.infrastructure.persistence.product;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.t3nexus.catalog.domain.product.ProductStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductJpaRepository extends JpaRepository<ProductJpaEntity, UUID> {

    Optional<ProductJpaEntity> findByIdAndSellerId(UUID id, UUID sellerId);

    /** Khóa dòng ({@code SELECT … FOR UPDATE}): lệnh đến sau chờ lệnh trước xong (ADR-0012). Cần giao dịch đang mở. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM ProductJpaEntity p WHERE p.id = :id AND p.sellerId = :sellerId")
    Optional<ProductJpaEntity> findForUpdate(@Param("id") UUID id, @Param("sellerId") UUID sellerId);

    /** Cột: id, title, status, version, số biến thể, giá thấp nhất, giá cao nhất, thời điểm sửa. */
    @Query("""
            SELECT p.id, p.title, p.status, p.version, COUNT(v.id), MIN(v.price), MAX(v.price), p.updatedAt
            FROM ProductJpaEntity p LEFT JOIN VariantJpaEntity v ON v.productId = p.id
            WHERE p.sellerId = :sellerId AND (:status IS NULL OR p.status = :status)
            GROUP BY p.id, p.title, p.status, p.version, p.updatedAt, p.createdAt
            ORDER BY p.createdAt DESC, p.id
            """)
    List<Object[]> findSummaries(@Param("sellerId") UUID sellerId, @Param("status") ProductStatus status,
                                 Pageable pageable);

    @Query("""
            SELECT COUNT(p) FROM ProductJpaEntity p
            WHERE p.sellerId = :sellerId AND (:status IS NULL OR p.status = :status)
            """)
    long countBySeller(@Param("sellerId") UUID sellerId, @Param("status") ProductStatus status);

    boolean existsByCategoryId(UUID categoryId);
}
