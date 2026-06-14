package vn.t3nexus.catalog.domain.brand;

import vn.t3nexus.lib.common.domain.exception.ErrorCode;

public enum BrandErrorCode implements ErrorCode {

    BRAND_NOT_FOUND          ("20001", "Brand not found",                 "error.brand.not_found",           404),
    BRAND_SLUG_ALREADY_EXISTS("20002", "Brand slug already exists",       "error.brand.slug_already_exists", 409),
    BRAND_IN_USE             ("20003", "Brand is referenced by products", "error.brand.in_use",              409);

    private final String code;
    private final String defaultMessage;
    private final String messageKey;
    private final int httpStatus;

    BrandErrorCode(String code, String defaultMessage, String messageKey, int httpStatus) {
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
