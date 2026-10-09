package vn.t3nexus.order.application.order;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import vn.t3nexus.lib.common.domain.cqrs.CommandHandler;
import vn.t3nexus.order.domain.order.Order;
import vn.t3nexus.order.domain.order.OrderException;
import vn.t3nexus.order.domain.order.OrderId;
import vn.t3nexus.order.domain.order.OrderInventoryTimeoutIndex;
import vn.t3nexus.order.domain.order.OrderRepository;
import vn.t3nexus.order.domain.order.OrderStatus;
import vn.t3nexus.order.domain.order.PaymentMethod;

/**
 * Consume InventoryReserved — quyết định rẽ nhánh COD (confirm ngay) hay PREPAID (chờ
 * payment flow, chưa implement).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConfirmOrderOnCodCreated implements CommandHandler<ConfirmOrderOnCodCreated.Command, ConfirmOrderOnCodCreated.Result> {

    private final OrderRepository orderRepository;
    private final OrderInventoryTimeoutIndex inventoryTimeoutIndex;

    @Override
    public Result handle(Command command) {
        Order order = orderRepository.findById(OrderId.of(command.orderId()))
                .orElseThrow(OrderException::notFound);

        if (order.getPaymentMethod() != PaymentMethod.COD) {
            log.info("[HandleInventoryReserved] paymentMethod={} (not COD), skip — handled by payment flow, orderId={}", order.getPaymentMethod(), command.orderId());
            return new Result();
        }

        if (!order.canProcess()) {
            // Late reply — Reservation đã được inventory-service tạo thật (khác OUT_OF_STOCK, không tạo
            // gì), nếu order đã bị timeout-cancel thì phải re-publish để trigger release, không thì
            // Reservation mồ côi giữ stock vĩnh viễn.
            if (order.getStatus() == OrderStatus.CANCELLED) {
                order.republishCancellation();
                orderRepository.save(order);
                log.info("[ConfirmOrderOnCodCreated] late reply — order đã CANCELLED trước đó, re-publish OrderCancelled để inventory-service release Reservation vừa tạo muộn. orderId={}", command.orderId());
            }
            return new Result();
        }
        order.confirm();
        orderRepository.save(order);
        inventoryTimeoutIndex.remove(command.orderId()); // dọn sớm — order đã resolved, không cần chờ quét
        return new Result();
    }

    public record Command(String orderId) {}

    public record Result() {}
}
