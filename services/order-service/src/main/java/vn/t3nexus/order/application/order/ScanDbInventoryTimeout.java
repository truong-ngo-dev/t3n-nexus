package vn.t3nexus.order.application.order;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import vn.t3nexus.lib.common.domain.cqrs.CommandHandler;
import vn.t3nexus.order.domain.order.OrderCancelReason;
import vn.t3nexus.order.domain.order.OrderId;
import vn.t3nexus.order.domain.order.OrderRepository;

import java.time.Instant;
import java.util.List;

/**
 * Lớp 2 (backstop) của cơ chế {@code CREATED}-timeout — xử lý 1 lần "đánh thức" từ
 * {@code taskType=ORDER_INVENTORY_TIMEOUT_DB_SCAN}. Chỉ bắt được order bị Lớp 1 (Redis) miss —
 * bình thường Lớp 1 đã xử lý trước, {@link CancelOrder#handle} tự no-op nếu order đã resolved.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScanDbInventoryTimeout implements CommandHandler<ScanDbInventoryTimeout.Command, ScanDbInventoryTimeout.Result> {

    private static final int BATCH_LIMIT = 100;

    private final OrderRepository orderRepository;
    private final CancelOrder cancelOrder;

    @Override
    public Result handle(Command command) {
        List<OrderId> dueOrderIds = orderRepository.findCreatedWithExpiredDeadline(Instant.now(), BATCH_LIMIT);
        dueOrderIds.forEach(orderId ->
                cancelOrder.handle(new CancelOrder.Command(orderId.getValue(), OrderCancelReason.INVENTORY_TIMEOUT)));
        if (!dueOrderIds.isEmpty()) {
            log.info("[ScanDbInventoryTimeout] cancelled {} order(s) missed by Lớp 1 (Redis): {}", dueOrderIds.size(), dueOrderIds);
        }
        return new Result(dueOrderIds.size());
    }

    public record Command() {}

    public record Result(int cancelledCount) {}
}
