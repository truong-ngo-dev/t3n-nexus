package vn.t3nexus.order.application.order;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import vn.t3nexus.lib.common.domain.cqrs.CommandHandler;
import vn.t3nexus.order.domain.order.Order;
import vn.t3nexus.order.domain.order.OrderException;
import vn.t3nexus.order.domain.order.OrderId;
import vn.t3nexus.order.domain.order.OrderRepository;
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

    @Override
    public Result handle(Command command) {
        Order order = orderRepository.findById(OrderId.of(command.orderId()))
                .orElseThrow(OrderException::notFound);

        if (order.getPaymentMethod() != PaymentMethod.COD) {
            log.info("[HandleInventoryReserved] paymentMethod={} (not COD), skip — handled by payment flow, orderId={}", order.getPaymentMethod(), command.orderId());
            return new Result();
        }

        if (!order.canProcess()) return new Result(); // late/duplicate reply — already resolved, no-op
        order.confirm();
        orderRepository.save(order);
        return new Result();
    }

    public record Command(String orderId) {}

    public record Result() {}
}
