package vn.t3nexus.order.application.order;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import vn.t3nexus.lib.common.domain.cqrs.CommandHandler;
import vn.t3nexus.order.domain.order.OrderCancelReason;
import vn.t3nexus.order.domain.order.OrderInventoryTimeoutIndex;

import java.time.Instant;
import java.util.List;

/**
 * Lớp 1 (tốc độ) của cơ chế {@code CREATED}-timeout — xử lý 1 lần "đánh thức" từ
 * {@code taskType=ORDER_INVENTORY_TIMEOUT_REDIS_SCAN}. Claim atomic qua
 * {@link OrderInventoryTimeoutIndex#pollDue}, mỗi orderId quá hạn đi qua {@link CancelOrder#handle}
 * — tự no-op nếu order đã resolved (đúng "Lớp 3" đã chốt ở Phase 5, không viết CAS riêng).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScanRedisInventoryTimeout implements CommandHandler<ScanRedisInventoryTimeout.Command, ScanRedisInventoryTimeout.Result> {

    private static final int BATCH_LIMIT = 100;

    private final OrderInventoryTimeoutIndex inventoryTimeoutIndex;
    private final CancelOrder cancelOrder;

    @Override
    public Result handle(Command command) {
        List<String> dueOrderIds = inventoryTimeoutIndex.pollDue(Instant.now(), BATCH_LIMIT);
        dueOrderIds.forEach(orderId ->
                cancelOrder.handle(new CancelOrder.Command(orderId, OrderCancelReason.INVENTORY_TIMEOUT)));
        if (!dueOrderIds.isEmpty()) {
            log.info("[ScanRedisInventoryTimeout] cancelled {} order(s): {}", dueOrderIds.size(), dueOrderIds);
        }
        return new Result(dueOrderIds.size());
    }

    public record Command() {}

    public record Result(int cancelledCount) {}
}
