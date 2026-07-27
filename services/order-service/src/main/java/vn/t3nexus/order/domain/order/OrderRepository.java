package vn.t3nexus.order.domain.order;

import vn.t3nexus.lib.common.domain.service.Repository;

/**
 * {@code delete()} kế thừa từ {@link Repository} nhưng không có nghĩa hợp lệ cho
 * aggregate event-sourced — xoá event stream phá đúng lý do chọn Event Sourcing cho
 * Order (mất audit trail). Adapter implement bằng cách throw {@link UnsupportedOperationException}.
 */
public interface OrderRepository extends Repository<Order, OrderId> {
}
