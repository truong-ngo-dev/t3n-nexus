package vn.t3nexus.inventory.application.stock;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.t3nexus.inventory.domain.stock.Stock;
import vn.t3nexus.inventory.domain.stock.StockException;
import vn.t3nexus.inventory.domain.stock.StockId;
import vn.t3nexus.inventory.domain.stock.StockRepository;
import vn.t3nexus.lib.common.domain.cqrs.CommandHandler;
import vn.t3nexus.lib.common.domain.service.ULIDGenerator;

@Slf4j
@Service
@RequiredArgsConstructor
public class InitializeStock implements CommandHandler<InitializeStock.Command, InitializeStock.Result> {

    private final StockRepository stockRepository;
    private final ULIDGenerator ulidGenerator;

    @Override
    @Transactional
    public Result handle(Command command) {
        if (stockRepository.existsBySkuId(command.skuId())) {
            throw StockException.alreadyExists();
        }

        StockId id = StockId.of(ulidGenerator.generate());
        Stock stock = Stock.initialize(id, command.skuId(), command.productId(), command.sellerId(),
                command.sellerActive(), command.productPublished());
        stockRepository.save(stock);

        log.info("[InitializeStock] created: stockId={}, skuId={}, traceId={}",
                id.getValue(), command.skuId(), MDC.get("traceId"));

        return new Result(id.getValue());
    }

    public record Command(String skuId, String productId, String sellerId,
                          boolean sellerActive, boolean productPublished) {}

    public record Result(String stockId) {}
}
