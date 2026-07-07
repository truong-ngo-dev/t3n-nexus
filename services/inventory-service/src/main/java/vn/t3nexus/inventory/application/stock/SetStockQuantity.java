package vn.t3nexus.inventory.application.stock;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.t3nexus.inventory.domain.stock.Stock;
import vn.t3nexus.inventory.domain.stock.StockException;
import vn.t3nexus.inventory.domain.stock.StockRepository;
import vn.t3nexus.lib.common.application.EventDispatcher;
import vn.t3nexus.lib.common.domain.cqrs.CommandHandler;

@Slf4j
@Service
@RequiredArgsConstructor
public class SetStockQuantity implements CommandHandler<SetStockQuantity.Command, SetStockQuantity.Result> {

    private final StockRepository stockRepository;
    private final EventDispatcher eventDispatcher;

    @Override
    @Transactional
    public Result handle(Command command) {
        Stock stock = stockRepository.findBySkuId(command.skuId())
                .orElseThrow(StockException::notFound);

        if (!stock.getSellerId().equals(command.sellerId())) {
            throw StockException.accessDenied();
        }

        stock.setQuantity(command.quantity());

        if (command.lowStockThreshold() != null) {
            stock.setLowStockThreshold(command.lowStockThreshold());
        }

        stockRepository.save(stock);
        eventDispatcher.dispatchAll(stock.getDomainEvents());
        stock.clearDomainEvents();

        log.info("[SetStockQuantity] updated: stockId={}, skuId={}, qty={}, available={}, threshold={}, traceId={}",
                stock.getId().getValue(), command.skuId(), command.quantity(),
                stock.availableQuantity(), stock.getLowStockThreshold(), MDC.get("traceId"));

        return new Result(stock.getId().getValue(), stock.availableQuantity());
    }

    public record Command(String skuId, int quantity, Integer lowStockThreshold, String sellerId) {}

    public record Result(String stockId, int availableQuantity) {}
}
