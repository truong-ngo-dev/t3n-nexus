package vn.t3nexus.catalog.infrastructure.persistence.brand;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.t3nexus.catalog.domain.brand.BrandStatus;

import java.util.List;
import java.util.UUID;

@Repository
public interface BrandJpaRepository extends JpaRepository<BrandJpaEntity, UUID> {

    boolean existsByNameKey(String nameKey);

    boolean existsByNameKeyAndIdNot(String nameKey, UUID id);

    // CAST(:keyword AS string): tham số null không suy ra được kiểu lúc prepare (cùng lỗi đã gặp ở
    // AttributeTemplateJpaRepository). Từ khóa đã là khóa tên chuẩn hóa nên so với cột name_key.
    @Query("""
            SELECT b FROM BrandJpaEntity b
            WHERE (:keyword IS NULL OR b.nameKey LIKE CONCAT('%', CAST(:keyword AS string), '%'))
              AND (:status IS NULL OR b.status = :status)
            ORDER BY b.nameKey, b.id
            """)
    List<BrandJpaEntity> search(@Param("keyword") String keyword,
                                @Param("status") BrandStatus status,
                                Pageable pageable);

    @Query("""
            SELECT COUNT(b) FROM BrandJpaEntity b
            WHERE (:keyword IS NULL OR b.nameKey LIKE CONCAT('%', CAST(:keyword AS string), '%'))
              AND (:status IS NULL OR b.status = :status)
            """)
    long countSearch(@Param("keyword") String keyword, @Param("status") BrandStatus status);
}
