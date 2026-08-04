package vn.t3nexus.order.infrastructure.persistence.order;

import tools.jackson.core.type.TypeReference;
import vn.t3nexus.lib.utils.JsonUtils;
import vn.t3nexus.lib.utils.reflect.ReflectionUtils;
import vn.t3nexus.order.domain.order.Order;
import vn.t3nexus.order.domain.order.OrderCancelReason;
import vn.t3nexus.order.domain.order.OrderId;
import vn.t3nexus.order.domain.order.OrderLineItem;
import vn.t3nexus.order.domain.order.OrderStatus;
import vn.t3nexus.order.domain.order.PaymentMethod;
import vn.t3nexus.order.domain.order.ShippingAddress;

import java.lang.reflect.Field;
import java.util.List;

public final class OrderMapper {

    private OrderMapper() {}

    private static final Field VERSION_FIELD = ReflectionUtils.findField(Order.class, "version");

    public static Order toDomain(OrderJpaEntity e) {
        Order order = Order.reconstitute(
                OrderId.of(e.getId()),
                e.getCustomerId(),
                e.getSellerId(),
                JsonUtils.fromJson(e.getItemsJson(), new TypeReference<List<OrderLineItem>>() {}),
                PaymentMethod.valueOf(e.getPaymentMethod()),
                JsonUtils.fromJson(e.getShippingAddressJson(), ShippingAddress.class),
                OrderStatus.valueOf(e.getStatus()),
                e.getCancelReason() == null ? null : OrderCancelReason.valueOf(e.getCancelReason()),
                e.getCreatedAt(),
                e.getUpdatedAt()
        );
        ReflectionUtils.setField(VERSION_FIELD, order, e.getVersion());
        return order;
    }

    public static OrderJpaEntity toJpaEntity(Order order) {
        OrderJpaEntity e = new OrderJpaEntity();
        e.setId(order.getId().getValue());
        e.setCustomerId(order.getCustomerId());
        e.setSellerId(order.getSellerId());
        e.setItemsJson(JsonUtils.toJson(order.getItems()));
        e.setPaymentMethod(order.getPaymentMethod().name());
        e.setShippingAddressJson(JsonUtils.toJson(order.getShippingAddress()));
        e.setStatus(order.getStatus().name());
        e.setCancelReason(order.getCancelReason() == null ? null : order.getCancelReason().name());
        e.setVersion((Long) ReflectionUtils.getField(VERSION_FIELD, order));
        e.setCreatedAt(order.getCreatedAt());
        e.setUpdatedAt(order.getUpdatedAt());
        return e;
    }
}
