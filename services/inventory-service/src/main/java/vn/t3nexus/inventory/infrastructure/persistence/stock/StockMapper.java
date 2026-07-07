package vn.t3nexus.inventory.infrastructure.persistence.stock;

import vn.t3nexus.inventory.domain.stock.Stock;
import vn.t3nexus.inventory.domain.stock.StockId;
import vn.t3nexus.lib.utils.reflect.ReflectionUtils;

import java.lang.reflect.Field;

public final class StockMapper {

    private StockMapper() {}

    private static final Field VERSION_FIELD = ReflectionUtils.findField(Stock.class, "version");

    public static Stock toDomain(StockJpaEntity e) {
        Stock stock = Stock.reconstitute(
                StockId.of(e.getId()),
                e.getSkuId(),
                e.getProductId(),
                e.getSellerId(),
                e.getTotalQuantity(),
                e.getReservedQuantity(),
                e.isSellerActive(),
                e.isProductPublished(),
                e.isAdminBlocked(),
                e.getLowStockThreshold(),
                e.getCreatedAt(),
                e.getUpdatedAt()
        );
        ReflectionUtils.setField(VERSION_FIELD, stock, e.getVersion());
        return stock;
    }

    public static StockJpaEntity toJpaEntity(Stock stock) {
        StockJpaEntity e = new StockJpaEntity();
        e.setId(stock.getId().getValue());
        e.setSkuId(stock.getSkuId());
        e.setProductId(stock.getProductId());
        e.setSellerId(stock.getSellerId());
        e.setTotalQuantity(stock.getTotalQuantity());
        e.setReservedQuantity(stock.getReservedQuantity());
        e.setSellerActive(stock.isSellerActive());
        e.setProductPublished(stock.isProductPublished());
        e.setAdminBlocked(stock.isAdminBlocked());
        e.setLowStockThreshold(stock.getLowStockThreshold());
        e.setVersion((Long) ReflectionUtils.getField(VERSION_FIELD, stock));
        e.setCreatedAt(stock.getCreatedAt());
        e.setUpdatedAt(stock.getUpdatedAt());
        return e;
    }
}
