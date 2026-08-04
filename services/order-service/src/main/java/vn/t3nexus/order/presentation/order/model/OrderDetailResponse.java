package vn.t3nexus.order.presentation.order.model;

import vn.t3nexus.order.application.order.GetOrder;
import vn.t3nexus.order.domain.order.OrderCancelReason;
import vn.t3nexus.order.domain.order.OrderLineItem;
import vn.t3nexus.order.domain.order.ShippingAddress;

import java.util.List;

public record OrderDetailResponse(
        String orderId, String customerId, String sellerId,
        List<OrderLineItem> items, String paymentMethod, ShippingAddress shippingAddress,
        String status, OrderCancelReason cancelReason
) {
    public static OrderDetailResponse from(GetOrder.Result result) {
        return new OrderDetailResponse(
                result.orderId(), result.customerId(), result.sellerId(),
                result.items(), result.paymentMethod(), result.shippingAddress(),
                result.status(), result.cancelReason());
    }
}
