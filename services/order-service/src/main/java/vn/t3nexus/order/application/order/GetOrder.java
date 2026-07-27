package vn.t3nexus.order.application.order;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vn.t3nexus.lib.common.domain.cqrs.QueryHandler;
import vn.t3nexus.order.domain.order.Order;
import vn.t3nexus.order.domain.order.OrderException;
import vn.t3nexus.order.domain.order.OrderId;
import vn.t3nexus.order.domain.order.OrderLineItem;
import vn.t3nexus.order.domain.order.OrderRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class GetOrder implements QueryHandler<GetOrder.Query, GetOrder.Result> {

    private final OrderRepository orderRepository;

    @Override
    public Result handle(Query query) {
        Order order = orderRepository.findById(OrderId.of(query.orderId()))
                .orElseThrow(OrderException::notFound);
        return new Result(
                order.getId().getValue(), order.getCustomerId(), order.getSellerId(),
                order.getItems(), order.getStatus().name(), order.getCancelReason(),
                order.getRevision());
    }

    public record Query(String orderId) {}

    public record Result(
            String orderId, String customerId, String sellerId,
            List<OrderLineItem> items, String status, String cancelReason,
            long revision) {}
}
