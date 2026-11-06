package vn.t3nexus.catalog.infrastructure.persistence.product;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ProductAttributeJpaRepository extends JpaRepository<ProductAttributeJpaEntity, ProductAttributeKey> {

    List<ProductAttributeJpaEntity> findByProductIdOrderByTemplateId(UUID productId);

    /**
     * Xóa ngay bằng một câu lệnh (không chờ flush) để ghi lại cùng khóa trong cùng giao dịch không đụng nhau. Xóa luôn
     * ngữ cảnh lưu trữ: các dòng đã nạp là bản cũ, giữ lại thì lần ghi sau sẽ gộp vào chúng thay vì chèn mới.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM ProductAttributeJpaEntity a WHERE a.productId = :productId")
    void deleteByProductId(@Param("productId") UUID productId);
}
