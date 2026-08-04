package vn.t3nexus.order.domain.order;

import vn.t3nexus.lib.common.domain.exception.DomainException;

public final class OrderException {

    private OrderException() {}

    public static DomainException notFound() {
        return new DomainException(OrderErrorCode.ORDER_NOT_FOUND);
    }

    public static DomainException emptyItems() {
        return new DomainException(OrderErrorCode.ORDER_EMPTY_ITEMS);
    }

    public static DomainException missingShippingAddress() {
        return new DomainException(OrderErrorCode.ORDER_MISSING_SHIPPING_ADDRESS);
    }

    public static DomainException invalidTransition(OrderStatus current, String action) {
        return new DomainException(OrderErrorCode.ORDER_INVALID_TRANSITION,
                "Cannot %s order in status %s".formatted(action, current));
    }
}
