package vn.t3nexus.inventory.application.stock;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.t3nexus.inventory.domain.stock.Stock;
import vn.t3nexus.inventory.domain.stock.StockRepository;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeactivateProductStocks {

    private final StockRepository stockRepository;

    @Transactional
    public void handle(Command command) {
        List<Stock> stocks = stockRepository.findAllByProductId(command.productId());
        if (stocks.isEmpty()) {
            log.info("[DeactivateProductStocks] no stocks found for productId={}", command.productId());
            return;
        }
        stocks.forEach(stock -> {
            stock.unpublishProduct();
            stockRepository.save(stock);
        });
        log.info("[DeactivateProductStocks] unpublished {} stocks for productId={}, traceId={}",
                stocks.size(), command.productId(), MDC.get("traceId"));
    }

    public record Command(String productId) {}
}
