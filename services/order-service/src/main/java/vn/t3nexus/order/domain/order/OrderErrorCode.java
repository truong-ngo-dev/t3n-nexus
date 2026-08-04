package vn.t3nexus.order.domain.order;

import vn.t3nexus.lib.common.domain.exception.ErrorCode;

public enum OrderErrorCode implements ErrorCode {

    ORDER_NOT_FOUND("ORDER_NOT_FOUND", "Order not found", "error.order.not_found", 404),
    ORDER_EMPTY_ITEMS("ORDER_EMPTY_ITEMS", "Order must have at least one item", "error.order.empty_items", 422),
    ORDER_MISSING_SHIPPING_ADDRESS("ORDER_MISSING_SHIPPING_ADDRESS", "Order must have a shipping address", "error.order.missing_shipping_address", 422),
    ORDER_INVALID_TRANSITION("ORDER_INVALID_TRANSITION", "Invalid order state transition", "error.order.invalid_transition", 422);

    private final String code;
    private final String defaultMessage;
    private final String messageKey;
    private final int httpStatus;

    OrderErrorCode(String code, String defaultMessage, String messageKey, int httpStatus) {
        this.code = code;
        this.defaultMessage = defaultMessage;
        this.messageKey = messageKey;
        this.httpStatus = httpStatus;
    }

    @Override public String code() { return code; }
    @Override public String defaultMessage() { return defaultMessage; }
    @Override public String messageKey() { return messageKey; }
    @Override public int httpStatus() { return httpStatus; }
}
