package vn.t3nexus.inventory.application.stock;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.t3nexus.inventory.domain.stock.StockRepository;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeactivateStock {

    private final StockRepository stockRepository;

    @Transactional
    public void handle(Command command) {
        stockRepository.findBySkuId(command.skuId()).ifPresentOrElse(stock -> {
            stock.deactivate();
            stockRepository.save(stock);
            log.info("[DeactivateStock] deactivated skuId={}, traceId={}",
                    command.skuId(), MDC.get("traceId"));
        }, () -> log.info("[DeactivateStock] stock not found for skuId={}, no-op", command.skuId()));
    }

    public record Command(String skuId) {}
}
