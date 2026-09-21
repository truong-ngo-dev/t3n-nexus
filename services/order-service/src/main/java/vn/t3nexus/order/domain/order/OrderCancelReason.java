package vn.t3nexus.order.domain.order;

public enum OrderCancelReason {
    OUT_OF_STOCK,
    INVENTORY_TIMEOUT,
    PAYMENT_INIT_FAILED,
    PAYMENT_REJECTED,
    PAYMENT_TIMEOUT
}
