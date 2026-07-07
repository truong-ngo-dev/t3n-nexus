package vn.t3nexus.inventory.application.stock;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import vn.t3nexus.inventory.domain.stock.Stock;
import vn.t3nexus.inventory.domain.stock.StockRepository;
import vn.t3nexus.lib.common.domain.cqrs.QueryHandler;

import java.util.List;

@Service
@RequiredArgsConstructor
public class GetStockList implements QueryHandler<GetStockList.Query, GetStockList.Result> {

    private final StockRepository stockRepository;

    @Override
    public Result handle(Query query) {
        PageRequest pageRequest = PageRequest.of(
                query.page(), query.size(), Sort.by("createdAt").descending());

        Page<Stock> page = query.productId() != null
                ? stockRepository.findBySellerIdAndProductId(query.sellerId(), query.productId(), pageRequest)
                : stockRepository.findBySellerId(query.sellerId(), pageRequest);

        List<StockSummary> items = page.getContent().stream()
                .map(s -> new StockSummary(
                        s.getSkuId(),
                        s.getTotalQuantity(),
                        s.availableQuantity(),
                        s.getLowStockThreshold(),
                        s.isSellable()))
                .toList();

        return new Result(items, page.getTotalElements());
    }

    public record Query(String sellerId, String productId, int page, int size) {}

    public record Result(List<StockSummary> items, long total) {}

    public record StockSummary(
            String skuId,
            int totalQuantity,
            int availableQuantity,
            int lowStockThreshold,
            boolean sellable
    ) {}
}
