package vn.t3nexus.catalog.infrastructure.persistence.product;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ProductImageJpaRepository extends JpaRepository<ProductImageJpaEntity, UUID> {

    List<ProductImageJpaEntity> findByProductIdOrderByPosition(UUID productId);

    /** Xóa ngay bằng một câu lệnh (như thuộc tính): ghi lại cùng vị trí trong cùng giao dịch không đụng ràng buộc duy nhất. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM ProductImageJpaEntity i WHERE i.productId = :productId")
    void deleteByProductId(@Param("productId") UUID productId);
}
