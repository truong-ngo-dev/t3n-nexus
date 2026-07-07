package vn.t3nexus.inventory.application.stock;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.t3nexus.inventory.domain.stock.Stock;
import vn.t3nexus.inventory.domain.stock.StockException;
import vn.t3nexus.inventory.domain.stock.StockRepository;
import vn.t3nexus.lib.common.domain.cqrs.CommandHandler;

@Slf4j
@Service
@RequiredArgsConstructor
public class ActivateStock implements CommandHandler<ActivateStock.Command, ActivateStock.Result> {

    private final StockRepository stockRepository;

    @Override
    @Transactional
    public Result handle(Command command) {
        Stock stock = stockRepository.findBySkuId(command.skuId())
                .orElseThrow(StockException::notFound);
        if (stock.isSellerActive()) {
            return Result.ALREADY_ACTIVE;
        }
        stock.activate();
        stockRepository.save(stock);
        if (stock.isAdminBlocked()) {
            log.warn("[ActivateStock] sellerActive set for skuId={} but remains non-sellable (adminBlocked)", command.skuId());
            return Result.ACTIVATED_BUT_ADMIN_BLOCKED;
        }
        log.info("[ActivateStock] reactivated skuId={}", command.skuId());
        return Result.ACTIVATED;
    }

    public record Command(String skuId) {}

    public enum Result { ACTIVATED, ACTIVATED_BUT_ADMIN_BLOCKED, ALREADY_ACTIVE }
}
