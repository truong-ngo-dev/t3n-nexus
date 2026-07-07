package vn.t3nexus.inventory.infrastructure.persistence.stock;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StockJpaRepository extends JpaRepository<StockJpaEntity, String> {

    Optional<StockJpaEntity> findBySkuId(String skuId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM StockJpaEntity s WHERE s.skuId = :skuId")
    Optional<StockJpaEntity> findBySkuIdForUpdate(@Param("skuId") String skuId);

    boolean existsBySkuId(String skuId);

    List<StockJpaEntity> findAllByProductId(String productId);

    Page<StockJpaEntity> findBySellerId(String sellerId, Pageable pageable);

    Page<StockJpaEntity> findBySellerIdAndProductId(String sellerId, String productId, Pageable pageable);
}
