package vn.t3nexus.order.application.order;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vn.t3nexus.lib.common.domain.cqrs.CommandHandler;
import vn.t3nexus.order.domain.order.Order;
import vn.t3nexus.order.domain.order.OrderException;
import vn.t3nexus.order.domain.order.OrderId;
import vn.t3nexus.order.domain.order.OrderRepository;

@Service
@RequiredArgsConstructor
public class ConfirmOrder implements CommandHandler<ConfirmOrder.Command, ConfirmOrder.Result> {

    private final OrderRepository orderRepository;

    @Override
    public Result handle(Command command) {
        Order order = orderRepository.findById(OrderId.of(command.orderId()))
                .orElseThrow(OrderException::notFound);
        order.confirm();
        orderRepository.save(order);
        return new Result();
    }

    public record Command(String orderId) {}

    public record Result() {}
}
