package vn.t3nexus.inventory.domain.stock;

import vn.t3nexus.lib.common.domain.exception.ErrorCode;

public enum StockErrorCode implements ErrorCode {

    STOCK_NOT_FOUND      ("30001", "Stock not found",              "error.stock.not_found",          404),
    STOCK_ALREADY_EXISTS ("30002", "Stock already exists for SKU", "error.stock.already_exists",     409),
    INSUFFICIENT_STOCK   ("30003", "Insufficient available stock", "error.stock.insufficient",       422),
    STOCK_INACTIVE       ("30004", "Stock is inactive",            "error.stock.inactive",           422),
    INVALID_QUANTITY     ("30005", "Quantity must be non-negative","error.stock.invalid_quantity",   400),
    STOCK_ACCESS_DENIED  ("30006", "Stock does not belong to seller","error.stock.access_denied",     403);

    private final String code;
    private final String defaultMessage;
    private final String messageKey;
    private final int httpStatus;

    StockErrorCode(String code, String defaultMessage, String messageKey, int httpStatus) {
        this.code           = code;
        this.defaultMessage = defaultMessage;
        this.messageKey     = messageKey;
        this.httpStatus     = httpStatus;
    }

    @Override public String code()           { return code; }
    @Override public String defaultMessage() { return defaultMessage; }
    @Override public String messageKey()     { return messageKey; }
    @Override public int httpStatus()        { return httpStatus; }
}
