package vn.t3nexus.order.domain.order;

import vn.t3nexus.lib.common.domain.model.AbstractId;

public final class OrderId extends AbstractId<String> {

    private OrderId(String value) {
        super(value);
    }

    public static OrderId of(String value) {
        return new OrderId(value);
    }
}
