package vn.t3nexus.inventory.domain.stock;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import vn.t3nexus.lib.common.domain.service.Repository;

import java.util.List;
import java.util.Optional;

public interface StockRepository extends Repository<Stock, StockId> {

    Optional<Stock> findBySkuId(String skuId);

    /** Acquires pessimistic write lock (SELECT FOR UPDATE). Must be called within a transaction. */
    Optional<Stock> findBySkuIdForUpdate(String skuId);

    boolean existsBySkuId(String skuId);

    List<Stock> findAllByProductId(String productId);

    Page<Stock> findBySellerId(String sellerId, Pageable pageable);

    Page<Stock> findBySellerIdAndProductId(String sellerId, String productId, Pageable pageable);
}
