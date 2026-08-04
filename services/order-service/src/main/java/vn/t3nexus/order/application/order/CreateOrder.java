package vn.t3nexus.order.application.order;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vn.t3nexus.lib.common.domain.cqrs.CommandHandler;
import vn.t3nexus.lib.common.domain.service.ULIDGenerator;
import vn.t3nexus.order.domain.order.Order;
import vn.t3nexus.order.domain.order.OrderId;
import vn.t3nexus.order.domain.order.OrderLineItem;
import vn.t3nexus.order.domain.order.OrderRepository;
import vn.t3nexus.order.domain.order.PaymentMethod;
import vn.t3nexus.order.domain.order.ShippingAddress;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CreateOrder implements CommandHandler<CreateOrder.Command, CreateOrder.Result> {

    private final OrderRepository orderRepository;
    private final ULIDGenerator ulidGenerator;

    @Override
    public Result handle(Command command) {
        OrderId id = OrderId.of(ulidGenerator.generate());
        Order order = Order.create(id, command.customerId(), command.sellerId(), command.items(),
                command.paymentMethod(), command.address());
        orderRepository.save(order);
        return new Result(id.getValue(), order.getStatus().name());
    }

    public record Command(String customerId, String sellerId, List<OrderLineItem> items,
                          PaymentMethod paymentMethod, ShippingAddress address) {}

    public record Result(String orderId, String status) {}
}
