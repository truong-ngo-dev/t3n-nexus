package vn.t3nexus.order.infrastructure.adapter.repository.order;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import vn.t3nexus.lib.common.application.EventDispatcher;
import vn.t3nexus.lib.common.domain.model.DomainEvent;
import vn.t3nexus.lib.common.domain.service.EventStore;
import vn.t3nexus.order.domain.order.Order;
import vn.t3nexus.order.domain.order.OrderId;
import vn.t3nexus.order.domain.order.OrderRepository;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class OrderRepositoryAdapter implements OrderRepository {

    private final EventStore eventStore;
    private final EventDispatcher eventDispatcher;

    @Override
    @Transactional(readOnly = true)
    public Optional<Order> findById(OrderId id) {
        List<DomainEvent> history = eventStore.loadEvents(id.getValue());
        if (history.isEmpty()) return Optional.empty();
        return Optional.of(Order.rehydrate(history));
    }

    @Override
    @Transactional
    public void save(Order order) {
        List<DomainEvent> pending = List.copyOf(order.getDomainEvents());
        if (pending.isEmpty()) return;

        // revision hiện tại (sau raise) trừ đi số event vừa raise = revision trước khi batch này xảy ra
        long expectedRevision = order.getRevision() - pending.size();

        eventStore.append(order.getId().getValue(), "Order", pending, expectedRevision);
        eventDispatcher.dispatchAll(pending);
        order.clearDomainEvents();
    }

    /**
     * Không hỗ trợ — xoá event stream của 1 aggregate event-sourced đồng nghĩa xoá lịch sử,
     * phá đúng lý do chọn Event Sourcing cho Order. Method tồn tại chỉ vì kế thừa
     * {@link vn.t3nexus.lib.common.domain.service.Repository}, cố tình không implement thật.
     */
    @Override
    public void delete(OrderId id) {
        throw new UnsupportedOperationException(
                "Order is event-sourced — deleting its event stream would destroy audit trail; not supported");
    }
}
