package vn.t3nexus.order.domain.order;

public enum OrderCancelReason {
    OUT_OF_STOCK,
    PAYMENT_INIT_FAILED,
    PAYMENT_REJECTED,
    PAYMENT_TIMEOUT
}
