package vn.t3nexus.inventory.application.stock;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.t3nexus.inventory.domain.stock.Stock;
import vn.t3nexus.inventory.domain.stock.StockRepository;
import vn.t3nexus.lib.common.domain.cqrs.CommandHandler;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ActivateStocksOnProductUnblocked implements CommandHandler<ActivateStocksOnProductUnblocked.Command, Void> {

    private final StockRepository stockRepository;

    @Override
    @Transactional
    public Void handle(Command command) {
        List<Stock> stocks = stockRepository.findAllByProductId(command.productId());
        for (Stock stock : stocks) {
            stock.unblock();
            stockRepository.save(stock);
        }
        log.info("[ActivateStocksOnProductUnblocked] unblocked={} for productId={}",
                stocks.size(), command.productId());
        return null;
    }

    public record Command(String productId) {}
}
