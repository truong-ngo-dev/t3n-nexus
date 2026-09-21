package vn.t3nexus.inventory.application.stock;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.t3nexus.inventory.domain.stock.StockRepository;

/**
 * Hard-delete Stock — chỉ được gọi khi catalog-service xoá cứng 1 Variant (chỉ xảy ra khi Product của nó
 * còn DRAFT, chưa từng publish nên chưa từng có Order/reservation nào chạm tới skuId đó). Idempotent —
 * skuId không tồn tại (đã xoá trước đó, hoặc Stock chưa kịp khởi tạo) thì no-op, không throw.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeleteStock {

    private final StockRepository stockRepository;

    @Transactional
    public void handle(Command command) {
        stockRepository.findBySkuId(command.skuId()).ifPresentOrElse(stock -> {
            stockRepository.delete(stock.getId());
            log.info("[DeleteStock] deleted skuId={}, traceId={}", command.skuId(), MDC.get("traceId"));
        }, () -> log.info("[DeleteStock] stock not found for skuId={}, no-op", command.skuId()));
    }

    public record Command(String skuId) {}
}
