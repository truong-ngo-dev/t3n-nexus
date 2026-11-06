package vn.t3nexus.catalog.domain.brand;

import vn.t3nexus.lib.common.domain.exception.ErrorCode;

public enum BrandErrorCode implements ErrorCode {

    BRAND_NOT_FOUND  ("BRAND_NOT_FOUND",   "Không tìm thấy thương hiệu",       "error.brand.not_found",  404),
    BRAND_NAME_TAKEN ("BRAND_NAME_TAKEN",  "Tên thương hiệu đã được sử dụng",  "error.brand.name_taken", 409);

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
