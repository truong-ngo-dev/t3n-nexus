package vn.t3nexus.order.infrastructure.adapter.repository.order;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import vn.t3nexus.lib.common.application.EventDispatcher;
import vn.t3nexus.lib.common.domain.model.DomainEvent;
import vn.t3nexus.order.domain.order.Order;
import vn.t3nexus.order.domain.order.OrderId;
import vn.t3nexus.order.domain.order.OrderRepository;
import vn.t3nexus.order.infrastructure.persistence.order.OrderJpaRepository;
import vn.t3nexus.order.infrastructure.persistence.order.OrderMapper;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class OrderRepositoryAdapter implements OrderRepository {

    private final OrderJpaRepository jpaRepository;
    private final EventDispatcher eventDispatcher;

    @Override
    @Transactional(readOnly = true)
    public Optional<Order> findById(OrderId id) {
        return jpaRepository.findById(id.getValue()).map(OrderMapper::toDomain);
    }

    @Override
    @Transactional
    public void save(Order order) {
        jpaRepository.save(OrderMapper.toJpaEntity(order));
        List<DomainEvent> pending = List.copyOf(order.getDomainEvents());
        eventDispatcher.dispatchAll(pending);
        order.clearDomainEvents();
    }

    @Override
    public void delete(OrderId id) {
        jpaRepository.deleteById(id.getValue());
    }
}
