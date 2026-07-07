package vn.t3nexus.inventory.application.stock;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vn.t3nexus.inventory.domain.stock.Stock;
import vn.t3nexus.inventory.domain.stock.StockException;
import vn.t3nexus.inventory.domain.stock.StockRepository;
import vn.t3nexus.lib.common.domain.cqrs.QueryHandler;

@Service
@RequiredArgsConstructor
public class GetStock implements QueryHandler<GetStock.Query, GetStock.Result> {

    private final StockRepository stockRepository;

    @Override
    public Result handle(Query query) {
        Stock stock = stockRepository.findBySkuId(query.skuId())
                .orElseThrow(StockException::notFound);

        if (!stock.getSellerId().equals(query.sellerId())) {
            throw StockException.accessDenied();
        }

        return toResult(stock);
    }

    private Result toResult(Stock stock) {
        return new Result(
                stock.getId().getValue(),
                stock.getSkuId(),
                stock.getProductId(),
                stock.getSellerId(),
                stock.getTotalQuantity(),
                stock.getReservedQuantity(),
                stock.availableQuantity(),
                stock.isSellerActive(),
                stock.isProductPublished(),
                stock.isAdminBlocked(),
                stock.isSellable(),
                stock.getLowStockThreshold()
        );
    }

    public record Query(String skuId, String sellerId) {}

    public record Result(
            String stockId,
            String skuId,
            String productId,
            String sellerId,
            int totalQuantity,
            int reservedQuantity,
            int availableQuantity,
            boolean sellerActive,
            boolean productPublished,
            boolean adminBlocked,
            boolean sellable,
            int lowStockThreshold
    ) {}
}
