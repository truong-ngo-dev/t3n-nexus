package vn.t3nexus.order.domain.order;

import vn.t3nexus.lib.common.domain.service.Repository;

import java.time.Instant;
import java.util.List;

public interface OrderRepository extends Repository<Order, OrderId> {

    /** Lớp 2 (backstop) của cơ chế {@code CREATED}-timeout — dùng partial index {@code idx_orders_inventory_timeout}. */
    List<OrderId> findCreatedWithExpiredDeadline(Instant asOf, int limit);
}
