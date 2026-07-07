package vn.t3nexus.inventory.infrastructure.adapter.repository.stock;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import vn.t3nexus.inventory.domain.stock.Stock;
import vn.t3nexus.inventory.domain.stock.StockId;
import vn.t3nexus.inventory.domain.stock.StockRepository;
import vn.t3nexus.inventory.infrastructure.persistence.stock.StockJpaRepository;
import vn.t3nexus.inventory.infrastructure.persistence.stock.StockMapper;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class StockPersistenceAdapter implements StockRepository {

    private final StockJpaRepository jpaRepository;

    @Override
    public Optional<Stock> findById(StockId id) {
        return jpaRepository.findById(id.getValue()).map(StockMapper::toDomain);
    }

    @Override
    public Optional<Stock> findBySkuId(String skuId) {
        return jpaRepository.findBySkuId(skuId).map(StockMapper::toDomain);
    }

    @Override
    public Optional<Stock> findBySkuIdForUpdate(String skuId) {
        return jpaRepository.findBySkuIdForUpdate(skuId).map(StockMapper::toDomain);
    }

    @Override
    public boolean existsBySkuId(String skuId) {
        return jpaRepository.existsBySkuId(skuId);
    }

    @Override
    public List<Stock> findAllByProductId(String productId) {
        return jpaRepository.findAllByProductId(productId).stream()
                .map(StockMapper::toDomain)
                .toList();
    }

    @Override
    public Page<Stock> findBySellerId(String sellerId, Pageable pageable) {
        return jpaRepository.findBySellerId(sellerId, pageable).map(StockMapper::toDomain);
    }

    @Override
    public Page<Stock> findBySellerIdAndProductId(String sellerId, String productId, Pageable pageable) {
        return jpaRepository.findBySellerIdAndProductId(sellerId, productId, pageable).map(StockMapper::toDomain);
    }

    @Override
    public void save(Stock stock) {
        jpaRepository.save(StockMapper.toJpaEntity(stock));
    }

    @Override
    public void delete(StockId id) {
        jpaRepository.deleteById(id.getValue());
    }
}
